// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise catches exceptions thrown from executor and turns
    them into reject
es6id: S25.4.3.1_A4.1_T1
author: Sam Mikes
description: new Promise(function () { throw }) should reject
---*/

var errorObject = {},
    p = new Promise(function () {
        throw errorObject;
    });

__result1 = true;
__result2 = false;

p.then(function() {
    // $ERROR("Unexpected fulfill -- promise should reject.");
    __result1 = false;
}, function (err) {
    if (err === errorObject) {
        // $ERROR("Expected promise rejection reason to be thrown errorObject, actually " + err);
        __result2 = true;
    }
})

__expect1 = true;
__expect2 = true;