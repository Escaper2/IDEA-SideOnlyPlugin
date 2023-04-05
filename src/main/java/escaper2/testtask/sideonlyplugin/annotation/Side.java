package escaper2.testtask.sideonlyplugin.annotation;

public enum Side {
    CLIENT,
    SERVER,
    BOTH,
    INVALID;

    public Side compareSides(Side other) {
        if (other == BOTH) return this;
        if (this == BOTH) return other;

        else if (this == INVALID || other == INVALID || this != other)
            return INVALID;
        else
            return this;
    }
}