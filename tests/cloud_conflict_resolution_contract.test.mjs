import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  validateFinalizeRequest, assertCurrentPromotion, CloudRequestError
} from "../supabase/functions/_shared/cloud_backup.mjs";

const BACKUP = "11111111-1111-4111-8111-111111111111";
const CURRENT = "22222222-2222-4222-8222-222222222222";
const read = (p) => readFileSync(p, "utf8");

test("explicit LOCAL_WINS requires exact expected CURRENT and rejects client authority", () => {
  assert.deepEqual(validateFinalizeRequest({ backup_id: BACKUP, resolution: "LOCAL_WINS",
    expected_current_backup_id: CURRENT }), {
      backup_id: BACKUP, resolution: "LOCAL_WINS", expected_current_backup_id: CURRENT
    });
  assert.deepEqual(validateFinalizeRequest({ backup_id: BACKUP }), { backup_id: BACKUP });
  const cases = [
    [{ backup_id: BACKUP, resolution: "LOCAL_WINS" }, "INVALID_UUID"],
    [{ backup_id: BACKUP, resolution: "LOCAL_WINS", expected_current_backup_id: "bad" }, "INVALID_UUID"],
    [{ backup_id: BACKUP, expected_current_backup_id: CURRENT }, "INVALID_RESOLUTION"],
    [{ backup_id: BACKUP, resolution: "CLOUD_WINS", expected_current_backup_id: CURRENT }, "INVALID_RESOLUTION"],
    [{ backup_id: BACKUP, resolution: "LOCAL_WINS", expected_current_backup_id: CURRENT,
      user_id: CURRENT }, "CLIENT_AUTHORITY_FIELD"],
  ];
  for (const [request, code] of cases) {
    assert.throws(() => validateFinalizeRequest(request),
      e => e instanceof CloudRequestError && e.code === code);
  }
  assert.throws(() => assertCurrentPromotion({status:"CURRENT_CHANGED"}),
    e => e instanceof CloudRequestError && e.code === "CURRENT_CHANGED");
});

test("force promotion is compare-and-swap, and prior CURRENT remains retained", () => {
  const sql = read("supabase/migrations/20261106120000_explicit_local_cloud_resolution.sql");
  assert.match(sql, /p_expected_current_backup_id uuid/);
  assert.match(sql, /current_id is distinct from p_expected_current_backup_id/);
  assert.match(sql, /candidate.status <> 'VERIFIED'/);
  assert.match(sql, /set status = 'RETAINED'/);
  assert.match(sql, /set parent_backup_id = current_id, status = 'CURRENT'/);
  assert.match(sql, /for update/);
  assert.match(sql, /revoke all on function public.cloud_promote_explicit_local_wins/);
  assert.match(sql, /to service_role/);
  assert.doesNotMatch(sql, /delete\s+from\s+public.cloud_backups/i);
});

test("ordinary finalize remains governed by the original lineage gate", () => {
  const fn = read("supabase/functions/cloud-backup-finalize/index.ts");
  assert.match(fn, /resolution === "LOCAL_WINS"/);
  assert.match(fn, /cloud_promote_explicit_local_wins/);
  assert.match(fn, /cloud_promote_backup/);
  assert.match(fn, /verifyObject\(/);
  assert.match(fn, /assertCurrentPromotion\(/);
});

test("Cloud-wins requires verified download, local CSV archive, full atomic replacement", () => {
  const ui = read("app/src/main/java/com/training/trackplanner/HomeScreen.kt");
  const viewModel = read("app/src/main/java/com/training/trackplanner/TrainingViewModel.kt");
  const repo = read("app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt");
  assert.match(ui, /saveLocalArchiveBeforeCloudReplace/);
  assert.match(ui, /keepCloudAndReplaceLocal/);
  assert.match(ui, /keepLocalAndReplaceCloud/);
  assert.match(viewModel, /exportRecordsBackup\(uri\)/);
  assert.match(repo, /replaceLocalWithCloudCurrent/);
  assert.match(repo, /downloadCloudBackup\(session, expectedCloudCurrentId\)/);
  assert.match(repo, /preflightRecovery\(downloaded.csv\)/);
  assert.match(repo, /localRecovery.protect\(RecoveryTrigger.BEFORE_CONFLICT_CLOUD_RESTORE\)/);
  assert.match(repo, /db.withTransaction/);
  assert.match(repo, /adoptVerifiedCloudCurrent/);
});

test("local operation details display calendar time, not epoch milliseconds", () => {
  const report = read("app/src/main/java/com/training/trackplanner/data/DataTransferReport.kt");
  const text = read("app/src/main/java/com/training/trackplanner/localization/LocalizedPresentation.kt");
  assert.match(report, /yyyy-MM-dd HH:mm:ss/);
  assert.match(text, /백업 날짜·시간/);
  assert.match(text, /formatTransferTimestamp\(report.startedAt/);
  assert.match(text, /formatTransferTimestamp\(stage.startedAt/);
});
