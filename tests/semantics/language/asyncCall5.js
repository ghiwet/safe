function res (x) { __result1 = x; }

function f2 () {
  @getLoop().push({ func: res, args: ["second"]});
}

function f1 () {
  @getLoop().push({ func: res, args: ["first"]});
  f2();
}

f1();

__result1 = "";
__expect1 = "second";
