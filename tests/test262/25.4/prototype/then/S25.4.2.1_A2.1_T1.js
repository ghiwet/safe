// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise reaction jobs have predictable environment
es6id: S25.4.2.1_A2.1_T1
author: Sam Mikes
description: argument thrown through "Thrower"
---*/

var obj = {};

__result1 = true;
__result2 = false;

var p = Promise.reject(obj).then(/*Identity, Thrower*/)
        .then(function () {
            // $ERROR("Unexpected fulfillment - promise should reject.");
            __result1 = false;
        }, function (arg) {
            if (arg === obj) {
                // $ERROR("Expected reject reason to be obj, actually " + arg);
                __result2 = true;
            }
        })

__expect1 = true;
__expect2 = true;