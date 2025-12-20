/**
 * Core utilities for building zero-copy, off-heap friendly components using the
 * Java Foreign
 * Function & Memory (FFM) API.
 */
module express.mvp.roray.ffm {
    exports express.mvp.roray.ffm.concurrent.queue;
    exports express.mvp.roray.ffm.ds.list;
    exports express.mvp.roray.ffm.ds.map;
    exports express.mvp.roray.ffm.pool;
    exports express.mvp.roray.ffm.utils.functions;
    exports express.mvp.roray.ffm.utils.memory;
}
