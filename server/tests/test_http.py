import http.client
import socket
import threading
import unittest
from wsgiref.simple_server import make_server
from fg_server.app import Application
from fg_server.http import BoundedWSGIServer, Handler


class HttpConcurrencyTest(unittest.TestCase):
    def test_idle_preconnection_does_not_block_health_request(self):
        server = make_server('127.0.0.1', 0, Application(None),
                             server_class=BoundedWSGIServer, handler_class=Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        idle = socket.create_connection(server.server_address, timeout=2)
        client = http.client.HTTPConnection(*server.server_address, timeout=2)
        try:
            client.request('GET', '/health')
            response = client.getresponse()
            self.assertEqual(200, response.status)
            self.assertEqual(b'{"version":1}', response.read())
        finally:
            idle.close()
            client.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
