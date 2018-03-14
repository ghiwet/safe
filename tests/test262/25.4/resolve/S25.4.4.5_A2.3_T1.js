// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
es6id: S25.4.4.5_A2.3_T1
author: Sam Mikes
description: Promise.resolve passes through an unsettled promise w/ same Constructor
---*/

var rejectP1,
    p1 = new Promise(function (resolve, reject) { rejectP1 = reject; }),
    p2 = Promise.resolve(p1),
    obj = {};

if (p1 === p2) {
    __result1 = true;
}

p2.then(function () {
    __result2 = false;
}, function (arg) {
    if (arg === obj) {
        __result2 = true;
    }
});

rejectP1(obj);

__expect1 = true;
__expect2 = true;

