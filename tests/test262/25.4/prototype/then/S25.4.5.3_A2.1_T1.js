// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise.prototype.then expects a constructor conforming to Promise as 'this'
es6id: S25.4.5.3_A2.1_T1
author: Sam Mikes
description: Promise.prototype.then throw if 'this' is non-Object
negative: TypeError
---*/

var p = new Promise(function () {});

var __result1 = false;

try {
    p.then.call(3, function () {}, function () {});
} catch (ex) {
    if (ex instanceof TypeError) {
        __result1 = true;
    }
}

var __expect1 = true;
