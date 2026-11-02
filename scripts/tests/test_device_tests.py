import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("device_tests", Path(__file__).resolve().parents[1] / "run_device_tests.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class DeviceTestReportTest(unittest.TestCase):
    def test_complete_success_accepts_repeated_status_and_summary_lines(self):
        output = "PASS icons\nPASS actions\nPASS icons\nPASS actions\nDEVICE_TESTS_RESULT: PASS cases=2\n"
        self.assertEqual(2, runner.validate_report(output))

    def test_failure_is_rejected_even_when_adb_exits_zero(self):
        with self.assertRaises(ValueError):
            runner.validate_report("FAIL actions\nDEVICE_TESTS_RESULT: FAIL cases=1\n")

    def test_partial_empty_or_crashed_runs_never_pass(self):
        for output in ["", "PASS icons\n", "Process crashed", "INSTRUMENTATION_FAILED: crash",
                       "DEVICE_TESTS_RESULT: PASS cases=0\n",
                       "PASS icons\nDEVICE_TESTS_RESULT: PASS cases=2\n",
                       "PASS icons\nDEVICE_TESTS_RESULT: PASS cases=1\nINSTRUMENTATION_CODE: 0\n"]:
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.validate_report(output)

    def test_command_failure_cannot_be_masked_by_success_output(self):
        with self.assertRaises(ValueError):
            runner.validate_report("PASS icons\nDEVICE_TESTS_RESULT: PASS cases=1\n", returncode=1)
