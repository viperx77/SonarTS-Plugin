#!/usr/bin/env bash

cat >/dev/null
cat <<EOF
{
 "highlights": [
  {
   "startLine": 2,
   "startCol": 0,
   "endLine": 2,
   "endCol": 8,
   "textType": "keyword"
  }
 ],

 "ncloc":[55, 77, 99],
 "commentLines":[24, 42],
 "nosonarLines":[24],
 "statements":100,
 "functions":10,
 "classes":1
}
EOF
