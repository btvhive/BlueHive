# Runs on the TV (pushed and started by the verifyReleaseOnDevice Gradle task).
# Verifies every class in /data/local/tmp/bh-verify.apk with the device's own
# dex2oat and prints one RESULT line plus the first rejected methods, then
# removes its scratch files. Never touches the installed BlueHive.
#
# The same script is embedded in bluehive-updates\publish-release.ps1, which runs
# it again before anything is published. Keep the two in step.
cd /data/local/tmp || exit 1
D2O=
for c in /apex/com.android.art/bin/dex2oat32 /apex/com.android.art/bin/dex2oat64 /system/bin/dex2oat; do
  [ -x "$c" ] && { D2O=$c; break; }
done
[ -n "$D2O" ] || { echo "RESULT no-dex2oat"; exit 0; }
# -T 1: only lines from now on. Never `logcat -c` - that would wipe the TV's
# crash evidence for whoever looks at it next.
logcat -T 1 -v brief -s dex2oat32:* dex2oat64:* dex2oat:* > bh-verify.log &
LP=$!
$D2O --dex-file=/data/local/tmp/bh-verify.apk --dex-location=/data/local/tmp/bh-verify.apk \
     --oat-file=/data/local/tmp/bh-verify.oat --compiler-filter=verify -j2 \
     --runtime-arg -verbose:verifier >/dev/null 2>&1
RC=$?
sleep 2
kill $LP
echo "RESULT rc=$RC classes=$(grep -c 'VerifyClass took' bh-verify.log) failed=$(grep -c 'failed to verify' bh-verify.log)"
grep 'failed to verify' bh-verify.log | sed 's/([^)]*)//g' | cut -c1-300 | head -5
rm -f bh-verify.apk bh-verify.oat bh-verify.vdex bh-verify.log bh-verify.sh
