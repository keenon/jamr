#! /bin/bash

# amr-release-1.0-test-proxy.txt
FILE="amr-release-tiny.txt"

echo "Testing tiny file"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE

FILE="amr-release-1.0-test-proxy.txt"

echo "Running real test"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE
