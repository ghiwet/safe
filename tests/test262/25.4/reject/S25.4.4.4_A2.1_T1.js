// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
   Promise.reject
es6id: S25.4.4.4_A2.1_T1
author: Sam Mikes
description: Promise.reject creates a new settled promise
---*/

var p = Promise.reject(3);

var __result1 = true;

if (!(p instanceof Promise)) {
    // $ERROR("Expected Promise.reject to return a promise.");
    __result1 = false;
}

var __expect1 = true;

__result2 = true;
__result3 = false;

p.then(function () {
    // $ERROR("Promise should not be fulfilled.");
    __result2 = false;
}, function (arg) {
    if (arg === 3) {
        // $ERROR("Expected promise to be rejected with supplied arg, got " + arg);
        __result3 = true;
    }
})

__expect2 = true;
__expect3 = true;
