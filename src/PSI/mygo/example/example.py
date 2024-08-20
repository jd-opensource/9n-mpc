import requests
import argparse
import api_pb2

if __name__ == "__main__":
    arg = argparse.ArgumentParser()
    arg.add_argument("--url", default="http://127.0.0.1:6325/psi")

    arg = arg.parse_args()

    body = api_pb2.PsiExecuteRequest(header=api_pb2.RequestHeader(request_id="task001", metadata={}), keys=[
                                     b"BOONNNNNNNNNNNNNNNGGGGGG", b"DREEEEEAAAAAMMM"])

    r = requests.post(arg.url, data=body.SerializeToString())

    rsp = api_pb2.PsiExecuteResult()
    rsp.ParseFromString(r.content)

    print(rsp)
