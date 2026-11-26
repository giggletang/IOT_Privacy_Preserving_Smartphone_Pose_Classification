"""Offline, no-write-to-phone/server PoseFL BASELINE vs GRAV_NORM ablation.
Uses original uploaded client_A/B csv and server SQLite round-0 weights.
Replicates Android Features, Kotlin 6-16-6 MLP, per-sample SGD, and Kotlin/JVM
java.util.Random(1) shuffle for each Trainer.train call. Never opens source DB writable.
"""
import csv, sqlite3, json, pathlib, collections, math
import numpy as np
from numba import njit

HERE = pathlib.Path(__file__).resolve().parent
SOURCE = HERE.parent / 'private-data'
DB_PATH = SOURCE / 'server' / 'posefl_round20_backup.sqlite3'
G = np.float32(9.81)

class JavaRandom:
    def __init__(self, seed): self.seed=(seed ^ 0x5DEECE66D)&((1<<48)-1)
    def bits(self,n):
        self.seed=(self.seed*0x5DEECE66D+0xB)&((1<<48)-1)
        return self.seed>>(48-n)
    def next_int(self,bound):
        if (bound & (bound-1))==0: return (bound*self.bits(31))>>31
        while True:
            bits=self.bits(31); v=bits % bound
            if bits-v+(bound-1)<(1<<31):return v
    def next_float(self):return self.bits(24)/float(1<<24)
    def shuffle(self,n):
        arr=np.arange(n,dtype=np.int32)
        for i in range(n,1,-1):
            j=self.next_int(i); arr[i-1],arr[j]=arr[j],arr[i-1]
        return arr

def local_init(seed=42):
    rng=JavaRandom(seed); w=np.zeros(214,dtype=np.float32)
    s1=np.float32(math.sqrt(6/(6+16)));s2=np.float32(math.sqrt(6/(16+6)))
    for i in range(96):w[i]=np.float32(np.float32(np.float32(rng.next_float())*2)-1)*s1
    for i in range(112,208):w[i]=np.float32(np.float32(np.float32(rng.next_float())*2)-1)*s2
    return w

def load():
    data={};invalid={}
    for device in 'AB':
        records=[];bad=[]
        with (SOURCE/f'client_{device}/{device}_windows.csv').open(newline='') as f:
            for line,row in enumerate(csv.reader(f),1):
                try:
                    if len(row)!=17:raise ValueError(f'Expected 17 fields, got {len(row)}')
                    label=int(row[2]);assert 0<=label<6
                    q=np.asarray(list(map(float,row[3:16])),dtype=np.float32)
                    if not np.isfinite(q).all():raise ValueError('non-finite sensor data')
                    int(row[16])
                    records.append((row[0],row[1],label,q))
                except Exception as exc:bad.append((line,str(exc)))
        data[device]=records;invalid[device]=bad
    return data,invalid

def extract(q,kind):
    am=q[0:3];gm=q[3:6]
    if kind=='BASELINE':return np.concatenate([am/G,gm/G]).astype(np.float32)
    unit=gm/np.maximum(np.sqrt(np.sum(gm*gm,dtype=np.float32)),np.float32(1e-6))
    lin=(am-gm)/G
    return np.concatenate([unit,lin]).astype(np.float32)

def select(items, cfg, split=None, session=None):
    a=[(extract(q,cfg),label) for sess,sp,label,q in items if (split is None or sp==split) and (session is None or sess==session)]
    if not a:return np.empty((0,6),np.float32),np.empty((0,),np.int32)
    return np.stack([v[0] for v in a]),np.asarray([v[1] for v in a],np.int32)

@njit(cache=True)
def sgd_epoch(w,X,y,order,lr):
    loss=np.float32(0.)
    for pos in order:
        x=X[pos]; label=y[pos]
        z1=np.empty(16,np.float32);h=np.empty(16,np.float32)
        for j in range(16):
            z=w[96+j]
            for i in range(6):z+=w[j*6+i]*x[i]
            z1[j]=z;h[j]=z if z>0 else np.float32(0)
        logits=np.empty(6,np.float32)
        for k in range(6):
            z=w[208+k]
            for j in range(16):z+=w[112+k*16+j]*h[j]
            logits[k]=z
        maxlog=np.max(logits)
        ex=np.empty(6,np.float32);summ=np.float32(0)
        for k in range(6):ex[k]=np.exp(logits[k]-maxlog);summ+=ex[k]
        dz2=ex/summ
        loss+=-np.log(dz2[label]+np.float32(1e-9))
        dz2[label]-=np.float32(1)
        dh=np.zeros(16,np.float32)
        for k in range(6):
            for j in range(16):dh[j]+=w[112+k*16+j]*dz2[k]
        for k in range(6):
            for j in range(16):w[112+k*16+j]-=lr*dz2[k]*h[j]
            w[208+k]-=lr*dz2[k]
        for j in range(16):
            dz1=dh[j] if z1[j]>0 else np.float32(0)
            for i in range(6):w[j*6+i]-=lr*dz1*x[i]
            w[96+j]-=lr*dz1
    return loss/len(y)

def train(weights,X,y,epochs,lr=np.float32(0.05)):
    w=weights.copy();rng=JavaRandom(1)
    loss=0.
    for _ in range(epochs): loss=sgd_epoch(w,X,y,rng.shuffle(len(y)),lr)
    return w,float(loss)

def predict(w,X):
    z=np.maximum(X@w[:96].reshape((16,6)).T+w[96:112],0)
    return np.argmax(z@w[112:208].reshape((6,16)).T+w[208:214],axis=1)

def metrics(w,X,y):
    yp=predict(w,X);cm=np.zeros((6,6),np.int32)
    for a,b in zip(y,yp):cm[a,b]+=1
    f1=[]
    for k in range(6):
        tp=cm[k,k];support=cm[k].sum();prd=cm[:,k].sum()
        if support:
            p=tp/prd if prd else 0.;r=tp/support
            f1.append(2*p*r/(p+r) if p+r else 0.)
    return dict(n=int(len(y)),correct=int(np.trace(cm)),accuracy=float(np.trace(cm)/len(y)),macro_f1=float(np.mean(f1)),cm=cm.tolist())

def write_csv(name,rows,fields):
    with (HERE/name).open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)


def run():
    data,bad=load()
    assert [len([i for i in data[d] if i[1]=='train']) for d in 'AB']==[180,184]
    with sqlite3.connect(f"file:{DB_PATH}?mode=ro",uri=True) as con:
        init=np.asarray(json.loads(con.execute('select weights_json from global_models where round=0').fetchone()[0]),dtype=np.float32)
    all_rows=[];cm_rows=[];round_rows=[];local_weights={};global_weights={}
    for cfg in ('BASELINE','GRAV_NORM'):
        print('CONFIG',cfg,flush=True)
        train_xy={d:select(data[d],cfg,split='train') for d in 'AB'}
        local_weights[cfg]={}
        for d in 'AB':
            X,y=train_xy[d]
            w,loss=train(local_init(),X,y,30)
            local_weights[cfg][d]=w
            print(' local',d,len(y),'loss',loss,flush=True)
        global_weights[cfg]={0:init.copy()}
        current=init.copy()
        for rnd in range(1,22):
            client_up=[]
            for d in 'AB':
                X,y=train_xy[d]
                w,loss=train(current,X,y,3)
                client_up.append((w,len(y)))
            current=((client_up[0][0].astype(np.float64)*client_up[0][1]+client_up[1][0].astype(np.float64)*client_up[1][1])/(client_up[0][1]+client_up[1][1])).astype(np.float32)
            global_weights[cfg][rnd]=current.copy()
            for d in 'AB':
                X,y=select(data[d],cfg,split='test')
                m=metrics(current,X,y)
                round_rows.append(dict(config=cfg,server_round=rnd,device=d,n=m['n'],correct=m['correct'],accuracy_pct=round(m['accuracy']*100,3),macro_f1=round(m['macro_f1'],5)))
            if rnd in (1,5,10,20,21):print(' global round',rnd,flush=True)
        for d in 'AB':
            for model_name,w in [(f'Local {d}',local_weights[cfg][d]),('Global',global_weights[cfg][21])]:
                for condition,split,sess in [('Test','test',None),('Standard','calib',f'{d}_cal01'),('Tilt','calib',f'{d}_tilt01'),('Shake','calib',f'{d}_shake01')]:
                    X,y=select(data[d],cfg,split=split,session=sess)
                    m=metrics(w,X,y)
                    all_rows.append(dict(config=cfg,model=model_name,device=d,condition=condition,server_round=21 if model_name=='Global' else '',n=m['n'],correct=m['correct'],accuracy_pct=round(m['accuracy']*100,3),macro_f1=round(m['macro_f1'],5)))
                    for tr in range(6):
                        cm_rows.append(dict(config=cfg,model=model_name,device=d,condition=condition,true_pose=tr,**{f'pred_{j}':m['cm'][tr][j] for j in range(6)}))
                    print(' result',model_name,d,condition,m['correct'],'/',m['n'],round(m['accuracy']*100,2),flush=True)
        # original GRAV_NORM stored weights should be close, not necessarily bit-identical
        if cfg=='GRAV_NORM':
            orig=global_weights[cfg][21]
            with sqlite3.connect(f"file:{DB_PATH}?mode=ro",uri=True) as con:
                wdb=np.asarray(json.loads(con.execute('select weights_json from global_models where round=21').fetchone()[0]),dtype=np.float32)
            print('GRAV_NORM replay divergence max_abs',float(np.max(np.abs(orig-wdb))),flush=True)
            (HERE/'replay_diagnostics.json').write_text(json.dumps({'global_round21_max_abs_weight_difference_vs_original':float(np.max(np.abs(orig-wdb))),'global_round21_mean_abs_weight_difference_vs_original':float(np.mean(np.abs(orig-wdb)))},indent=2))
    write_csv('model_feature_ablation.csv',all_rows,['config','model','device','condition','server_round','n','correct','accuracy_pct','macro_f1'])
    write_csv('confusion_matrices.csv',cm_rows,['config','model','device','condition','true_pose']+[f'pred_{j}' for j in range(6)])
    write_csv('fedavg_rounds.csv',round_rows,['config','server_round','device','n','correct','accuracy_pct','macro_f1'])
    for cfg in ('BASELINE','GRAV_NORM'):
        for d in 'AB': np.savetxt(HERE/f'{cfg}_local_{d}_offline_weights.csv',local_weights[cfg][d],delimiter=',')
        np.savetxt(HERE/f'{cfg}_global_round21_offline_weights.csv',global_weights[cfg][21],delimiter=',')
    (HERE/'data_quality.json').write_text(json.dumps({d:{'valid_rows':len(data[d]),'issues':bad[d], 'split_counts':dict(collections.Counter(r[1] for r in data[d]))} for d in 'AB'},indent=2))
    print('DONE',flush=True)

if __name__=='__main__':run()
