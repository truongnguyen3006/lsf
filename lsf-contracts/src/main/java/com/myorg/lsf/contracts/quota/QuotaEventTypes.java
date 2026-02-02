package com.myorg.lsf.contracts.quota;

public class QuotaEventTypes {
    private QuotaEventTypes() {}

    public static final String RESERVE_COMMAND_V1 = "quota.reserve.command.v1";
    public static final String RESERVE_RESULT_V1 = "quota.reserve.result.v1";
    public static final String CONFIRM_COMMAND_V1 = "quota.confirm.command.v1";
    public static final String RELEASE_COMMAND_V1 = "quota.release.command.v1";
}
