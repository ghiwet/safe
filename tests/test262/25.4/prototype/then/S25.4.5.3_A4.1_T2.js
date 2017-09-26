// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    PerformPromiseThen
    Ref 25.4.5.3.1
es6id: S25.4.5.3_A4.1_T2
author: Sam Mikes
description: Promise.prototype.then accepts 'undefined' as arg1, arg2
---*/

var obj = {};
var p = Promise.reject(obj);

__result1 = true;
__result2 = false;

p.then(undefined, undefined).then(function () {
        __result1 = false;
}, function (arg) {
        if (arg === obj) {
            __result2 = true;
        }
    })

__expect1 = true;
__expect2 = true;
