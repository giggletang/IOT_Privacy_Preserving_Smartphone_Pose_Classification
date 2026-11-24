import math
from fastapi.testclient import TestClient
from server import create_app, initial_weights, combine


def test_initial_weights():
    w = initial_weights()
    assert len(w) == 214 and all(math.isfinite(x) for x in w)
    assert w[96:112] == [0] * 16
    assert w[208:214] == [0] * 6


def test_fedavg_math():
    assert combine([1.0] * 214, [3.0] * 214, 1, 3) == [2.5] * 214


def test_complete_round_and_restart(tmp_path):
    db = tmp_path / "test.sqlite3"
    app = create_app(db)
    with TestClient(app) as client:
        first = client.get('/model').json()
        assert first['round'] == 0 and len(first['weights']) == 214
        assert client.post('/update', json={"client_id":"A", "round":0, "n_samples":100,"weights":[1.0]*214}).json()['status'] == 'waiting_for_other_client'
        assert client.get('/status').json()['waiting_for'] == ['B']
        assert client.post('/update', json={"client_id":"A", "round":0, "n_samples":100,"weights":[1.0]*214}).json()['status'] == 'already_received'
        assert client.post('/update', json={"client_id":"A", "round":0, "n_samples":100,"weights":[5.0]*214}).status_code == 409
        assert client.post('/update', json={"client_id":"B", "round":0, "n_samples":300,"weights":[3.0]*214}).json()['status'] == 'aggregated'
        second = client.get('/model').json()
        assert second['round'] == 1 and second['weights'] == [2.5]*214
        assert client.post('/update', json={"client_id":"A", "round":0, "n_samples":100,"weights":[1.0]*214}).status_code == 409
    with TestClient(create_app(db)) as client:
        assert client.get('/model').json()['round'] == 1
        assert client.get('/model').json()['weights'] == [2.5] * 214


def test_invalid_requests(tmp_path):
    with TestClient(create_app(tmp_path / 'bad.sqlite3')) as client:
        base = {"client_id":"A", "round":0, "n_samples":20, "weights":[0.0]*214}
        for field, value in [("client_id","C"),("n_samples",0),("weights",[0.0]*5)]:
            body = dict(base);body[field] = value
            assert client.post('/update',json=body).status_code == 422
        body = dict(base);body['round']=4
        assert client.post('/update',json=body).status_code==409
