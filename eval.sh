#! /bin/bash

# amr-release-1.0-test-proxy.txt
FILE="amr-release-tiny.txt"

echo "Testing $FILE"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE

FILE="amr-release-1.0-small.txt"

echo "Testing $FILE"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE

FILE="amr-release-1.0-test-bolt.txt"

echo "Testing $FILE"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE

FILE="amr-release-1.0-test-xinhua.txt"

echo "Testing $FILE"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE

FILE="amr-release-1.0-test-dfa.txt"

echo "Testing $FILE"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE

FILE="amr-release-1.0-test-proxy.txt"

echo "Testing $FILE"

source scripts/config.sh
scripts/EVAL.sh /big2/nlp/jamr/models/ACL2014_LDC2013E117/$FILE
