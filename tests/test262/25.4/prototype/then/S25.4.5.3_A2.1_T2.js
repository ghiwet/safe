// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise.prototype.then expects a Promise as 'this'
es6id: S25.4.5.3_A2.1_T2
author: Sam Mikes
description: Promise.prototype.then throw if 'this' is non-Promise Object
negative: TypeError
---*/

function ZeroArgConstructor() {
}

var z = new ZeroArgConstructor();

var __result1 = false;

try {
    Promise.then.call(z, function () {}, function () {});
} catch (ex) {
    if (ex instanceof TypeError) {
        __result1 = true;
    }
}

var __expect1 = true;
