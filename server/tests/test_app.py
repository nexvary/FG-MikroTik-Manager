import io
import json
import unittest
from datetime import datetime,timezone
from decimal import Decimal
from fg_server.app import Application

class RadiusReadApiTest(unittest.TestCase):
    def test_expiry_and_postgres_aggregate_are_valid_json(self):
        class Fixture:
            def radius_users(self,token):
                return [{'username':'test','expires':datetime(2026,10,4,tzinfo=timezone.utc),'seconds':Decimal(123)}]
        status=[]
        response=Application(Fixture())({'REQUEST_METHOD':'GET','PATH_INFO':'/v1/radius/users','HTTP_AUTHORIZATION':'Bearer fixture','wsgi.input':io.BytesIO()},lambda s,h:status.append(s))
        self.assertEqual(['200 OK'],status)
        result=json.loads(b''.join(response))
        self.assertEqual(123,result['users'][0]['seconds'])
        self.assertEqual('2026-10-04T00:00:00+00:00',result['users'][0]['expires'])
