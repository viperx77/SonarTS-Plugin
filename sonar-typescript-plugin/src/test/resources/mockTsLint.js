
var testFile = process.argv[2];
var result = [
    {
        startPosition: {line: 1, character: 5},
        endPosition: {line: 1, character: 6},
        name: testFile,
        ruleName: "no-unconditional-jump"
    }
];

console.log(JSON.stringify(result));
