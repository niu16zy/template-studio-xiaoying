package com.xiaoying.studio.web;

import java.util.Map;

public record ApiError(String code, String message, Map<String, String> fieldErrors, String requestId) {}
