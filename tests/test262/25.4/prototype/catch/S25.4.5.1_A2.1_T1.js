// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    catch is a method on a Promise
es6id: S25.4.5.1_A2.1_T1
author: Sam Mikes
description: catch is a method on a Promise
---*/
var __result1 = true;
var __result2 = true;

var p = Promise.resolve(3);

if (!(p.catch instanceof Function)) {
    //$ERROR("Expected p.catch to be a function");
    __result1 = false;
}

if (p.catch.length !== 1) {
    //$ERROR("Expected p.catch to take one argument");
    __result2 = false;
}
var __expect1 = true;
var __expect2 = true;

