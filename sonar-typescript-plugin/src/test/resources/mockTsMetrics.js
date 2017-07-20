#!/usr/bin/env node

var result = {
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
};

process.stdin.on('data', function () {
 // needed for 'end' to be sent
});

process.stdin.on('end', function () {
    process.stdout.write(JSON.stringify(result));
});
