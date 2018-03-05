function foo() { __result1 = x; }

function bar () {
  @getLoop().push({ func: foo, args: []});
}

x = "before";
bar();
x = "after";

__result1 = "before";
__expect1 = "after";
