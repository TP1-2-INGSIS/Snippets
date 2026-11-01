package com.ingsis.snippets.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiErrorTest {

	@Test
	void nullDetailsBecomeEmptyList() {
		assertThat(new ApiError("X", "m", null).details()).isEmpty();
	}

	@Test
	void conflictExceptionOnlyAcceptsConflictCodes() {
		assertThatThrownBy(() -> new ConflictException(ErrorCode.NOT_FOUND, "nope"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
