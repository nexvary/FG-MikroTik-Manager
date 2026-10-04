"""Bounded WSGI API. Bind to loopback; expose only through an HTTPS reverse proxy."""
import json
from .service import Service

class Application:
    def __init__(self, service): self.service=service
    def __call__(self, env, start):
        status='200 OK'
        try:
            path=env.get('PATH_INFO','');method=env.get('REQUEST_METHOD','')
            auth=env.get('HTTP_AUTHORIZATION','')
            token=auth[7:] if auth.startswith('Bearer ') else ''
            if method=='GET' and path=='/health': result={'version':1}
            elif method=='POST' and path in {'/v1/login','/v1/events'}:
                if env.get('CONTENT_TYPE','').split(';')[0]!='application/json': raise ValueError('JSON_REQUIRED')
                length=int(env.get('CONTENT_LENGTH','0'))
                if not 1<=length<=1048576: raise ValueError('BODY_LIMIT')
                raw=env['wsgi.input'].read(length)
                if len(raw)!=length: raise ValueError('INCOMPLETE_BODY')
                body=json.loads(raw)
                if path=='/v1/login':
                    if not isinstance(body,dict) or set(body)!={'username','password'}: raise ValueError('INVALID_LOGIN')
                    result={'token':self.service.login(body['username'],body['password']),'expires_in':900}
                else: result={'events':self.service.ingest(token,body)}
            elif method=='GET' and path=='/v1/events':
                from urllib.parse import parse_qs
                query=parse_qs(env.get('QUERY_STRING',''),strict_parsing=True)
                result={'events':self.service.page(token,int(query.get('after',['0'])[0]))}
            else: status='404 Not Found';result={'error':'NOT_FOUND'}
        except PermissionError:
            status='403 Forbidden';result={'error':'ACCESS_DENIED'}
        except (ValueError,TypeError,KeyError):
            status='400 Bad Request';result={'error':'INVALID_REQUEST'}
        except Exception:
            status='503 Service Unavailable';result={'error':'RETRY_LATER'}
        data=json.dumps(result,separators=(',',':')).encode()
        start(status,[('Content-Type','application/json'),('Content-Length',str(len(data))),('Cache-Control','no-store')])
        return [data]
