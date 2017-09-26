// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    PerformPromiseThen
    Ref 25.4.5.3.1
es6id: S25.4.5.3_A4.2_T2
author: Sam Mikes
description: Promise.prototype.then treats non-callable arg1, arg2 as undefined
---*/

var obj = {};
var p = Promise.reject(obj);

__result1 = true;
__result2 = false;

p.then(3, 5).then(function () {
    __result1 = false;
}, function (arg) {
    if (arg === obj) {
        // $ERROR("Expected resolution object to be passed through, got " + arg);
        __result2 = true;
    }
})

__expect1 = true;
__expect2 = true;
