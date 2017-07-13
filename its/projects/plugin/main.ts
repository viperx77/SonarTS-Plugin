function foo(x: number): boolean {
  if (x < x) {
    return true;
  }

  if (x < x) { // NOSONAR
    return true;
  }

  return false;
}
