# Repository Knowledge Map

`roray-ffm-utils` is the low-level Java FFM utility library used by the MVP Express stack.

Source-verified modules:
- `lib` - memory utilities, native function helpers, pools, queues, and off-heap data structures
- `benchmarks` - JMH benchmarks

Start here:
- `README.md` - public overview
- `docs/guide.md` - usage guide
- `docs/internals/zero-gc-verification.md` - allocation/GC notes
- `docs/quality/README.md` - validation harness

Important package roots:
- `express.mvp.roray.ffm.utils.memory`
- `express.mvp.roray.ffm.utils.functions`
- `express.mvp.roray.ffm.concurrent.queue`
- `express.mvp.roray.ffm.pool`
- `express.mvp.roray.ffm.ds.map`
- `express.mvp.roray.ffm.ds.list`

Agent rule:
- do not rely on compiled `bin/` artifacts as source truth
