var sequence = [];

function checkSequence (arr) {
  __result1 = true;
  for (var i = 0; i < arr.length; i++) {
    if (arr[i] !== i + 1) {
      __result1 = false;
    }
  }
}

function foo (x) { sequence.push(x); }

function bar () {
  for (var i = 1; i <= 2; i++) {
    @getLoop().push({ func: foo, args: [i]});
  }
}

bar();

@getLoop().push({ func: checkSequence, args: [sequence]});

__result1 = false;
__expect1 = true;
