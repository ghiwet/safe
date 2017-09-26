// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
   Promise.reject
es6id: S25.4.4.4_A3.1_T1
author: Sam Mikes
description: Promise.reject throws TypeError for bad 'this'
negative: TypeError
---*/

function ZeroArgConstructor() {
}

var __result1 = false;

try {
    Promise.reject.call(ZeroArgConstructor, 4);
} catch (ex) {
    if (ex instanceof TypeError) {
        __result1 = true;
    }
}

var __expect1 = true;