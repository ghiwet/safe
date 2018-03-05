function f1 () { __result1 = "OK"; }

function f2 () { __result2 = "OK"; }

function bar () {
  @getLoop().push({ func: f1, args: []});
  @getLoop().push({ func: f2, args: []});
}

bar();

__result1 = "Error";
__expect1 = "OK";

__result2 = "Error";
__expect2 = "OK";
