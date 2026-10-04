import unittest
from fg_server.contracts import canonical

class ContractsTest(unittest.TestCase):
    def event(self):
        return dict(version=1,id='e1',device='d1',kind='ledger.append',body=dict(subscriber='s1',currency='EGP',amount_minor=100,reversal_of=None,note='cash'))
    def test_integer_only_and_bounded(self):
        for amount in [True,0,1.1,10**16]:
            e=self.event();e['body']['amount_minor']=amount
            with self.assertRaises(ValueError): canonical(e)
    def test_digest_is_order_independent(self):
        e=self.event();self.assertEqual(canonical(e),canonical(dict(reversed(list(e.items())))))
    def test_unknown_fields_rejected(self):
        e=self.event();e['tenant']='other'
        with self.assertRaises(ValueError): canonical(e)
