function foo(x) { __result1 = x; }

function bar () {
  @getLoop().push({ func: foo, args: ["OK"]});
}

bar();

__result1 = "Error";
__expect1 = "OK";
