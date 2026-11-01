#!/bin/sh
set -u
./mvnw -B verify
result=$?
if [ -d /reports ]; then
  if [ -d target/surefire-reports ]; then cp -R target/surefire-reports /reports/; fi
  if [ -d target/failsafe-reports ]; then cp -R target/failsafe-reports /reports/; fi
fi
exit "$result"
