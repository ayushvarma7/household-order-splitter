package com.householdsplitter.util;

/** A one-shot result delivered on the main thread. */
public interface Callback<T> {

    void onResult(T value);
}
