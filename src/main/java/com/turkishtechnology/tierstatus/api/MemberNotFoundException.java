package com.turkishtechnology.tierstatus.api;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(String memberId) {
        super("Member not found: " + memberId);
    }
}
