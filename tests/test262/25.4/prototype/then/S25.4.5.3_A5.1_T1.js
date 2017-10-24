// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    PerformPromiseThen
    Ref 25.4.5.3.1
es6id: S25.4.5.3_A5.1_T1
author: Sam Mikes
description: Promise.prototype.then enqueues handler if pending
includes: [PromiseHelper.js]
---*/

var sequence = [],
    pResolve,
    p = new Promise(function (resolve, reject) {
        pResolve = resolve;
    });

sequence.push(1);

p.then(function () {
    sequence.push(3);
    // checkSequence(sequence, "Should be second");
    __result2 = (sequence.length === 3);
    for (var i = 0; i < 3; i++) {
        if (sequence[i] !== i + 1) {
            __result2 = false;
        }
    }
})

Promise.resolve().then(function () {
    // enqueue another then-handler
    p.then(function () {
        sequence.push(4);
        // checkSequence(sequence, "Should be third");
        __result3 = (sequence.length === 4);
        for (var i = 0; i < 4; i++) {
            if (sequence[i] !== i + 1) {
                __result3 = false;
            }
        }
    })

    sequence.push(2);
    //checkSequence(sequence, "Should be first");
    __result1 = (sequence.length === 2);
    for (var i = 0; i < 2; i++) {
        if (sequence[i] !== i + 1) {
            __result1 = false;
        }
    }

    pResolve();
})

__expect1 = true;
__expect2 = true;
__expect3 = true;