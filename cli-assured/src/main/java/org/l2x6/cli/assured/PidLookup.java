package org.l2x6.cli.assured;

class PidLookup {

    public static long getPid(Process process) {
        throw new UnsupportedOperationException(Process.class.getName() + ".pid() is not supported before Java version 9; current Java version: ");
    }

}
