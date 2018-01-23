function foo (x) { __result1 = x; }

@getLoop().push({ func: foo, args: ["one"]});

@getLoop().push({ func: foo, args: ["two"]});

@getLoop().push({ func: foo, args: ["three"]});

__result1 = "";
__expect1 = "three";
