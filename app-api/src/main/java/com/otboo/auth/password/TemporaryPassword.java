package com.otboo.auth.password;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 임시 비밀번호 생성기.
 *
 * <h2>왜 헷갈리는 글자를 빼는가</h2>
 * 사용자가 메일에서 눈으로 읽고 옮겨 적는 값이다. {@code 0}/{@code O}/{@code o} 와
 * {@code 1}/{@code l}/{@code I} 는 글꼴에 따라 구분이 안 된다. 잘못 읽으면
 * "메일이 왔는데 로그인이 안 된다"는 문의로 돌아온다.
 *
 * <h2>규칙을 반드시 만족해야 한다</h2>
 * {@code UserCreateRequest} 의 비밀번호 규칙(영문+숫자 포함, 6자 이상)을 만족하지 않으면
 * 사용자가 이 비밀번호로 로그인한 뒤 <b>비밀번호 변경 화면에서 막힌다.</b>
 * 그래서 영문·숫자를 운에 맡기지 않고 하나씩 먼저 확보한다.
 */
public final class TemporaryPassword {

    private static final char[] LETTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();

    /** 12자. 사람이 옮겨 적을 수 있는 한도 안에서 충분히 길게 잡는다. */
    private static final int LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        List<Character> characters = new ArrayList<>(LENGTH);
        characters.add(pick(LETTERS));
        characters.add(pick(DIGITS));
        while (characters.size() < LENGTH) {
            characters.add(RANDOM.nextBoolean() ? pick(LETTERS) : pick(DIGITS));
        }
        // 먼저 확보한 두 글자가 늘 앞에 오면 "1번째는 영문, 2번째는 숫자"로 자리가 고정된다.
        Collections.shuffle(characters, RANDOM);

        StringBuilder password = new StringBuilder(LENGTH);
        characters.forEach(password::append);
        return password.toString();
    }

    private static char pick(char[] pool) {
        return pool[RANDOM.nextInt(pool.length)];
    }

    private TemporaryPassword() {
    }
}
