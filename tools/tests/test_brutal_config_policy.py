import json
import tempfile
import unittest
from pathlib import Path

from tools import brutal_test_policy as policy
from tools.brutal_test_runner import validate_config


class BrutalConfigPolicyTest(unittest.TestCase):
    def _base(self):
        return json.loads(Path('tools/brutal_test_config.json').read_text(encoding='utf-8'))

    def test_valid_config_passes(self):
        cfg = self._base()
        validate_config(cfg)

    def test_a14_contract_kill_gate_fails(self):
        cfg = self._base()
        cfg['kill_gates']['a14_contract'] = ['python', 'tools/brutal_a14_contract_scan.py', '--mutation', '{mutator}']
        with self.assertRaises(Exception):
            validate_config(cfg)

    def test_lower_coverage_target_fails(self):
        cfg = self._base()
        cfg['coverage_target'] = 73
        with self.assertRaises(Exception):
            validate_config(cfg)

    def test_missing_required_independent_mutation_fails(self):
        cfg = self._base()
        cfg['required_independent_mutations'] = [m for m in cfg['required_independent_mutations'] if m != 'swallow fatal']
        with self.assertRaises(Exception):
            validate_config(cfg)

    def test_incomplete_ledger_fails(self):
        cfg = self._base()
        cfg['coverage_ledger'] = cfg['coverage_ledger'][:-1]
        with self.assertRaises(Exception):
            validate_config(cfg)

    def test_unknown_mutation_in_ledger_requires_explanation(self):
        cfg = self._base()
        cfg['coverage_ledger'].append({'id': 'unknown mutation', 'status': 'MUTATOR_STALE'})
        with self.assertRaises(Exception):
            validate_config(cfg)
