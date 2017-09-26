// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise prototype object is an ordinary object
es6id: S25.4.5_A2.1_T1
author: Sam Mikes
description: Promise prototype is a standard built-in Object
---*/

var __result1 = true;

if (!(Promise.prototype instanceof Object)) {
    // $ERROR("Expected Promise.prototype to be an Object");
    __result1 = false;
}

var __expect1 = true;