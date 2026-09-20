package com.auto.secop;

import com.auto.secop.model.parametria.ParametriaProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SecopApplicationTests {

	@Autowired
	private ParametriaProfile profile;

	@Test
	void contextLoadsParametria() {
		assertThat(profile.version()).isEqualTo("2.0.1");
		assertThat(profile.filtros().safePalabrasClaveIncluir()).isNotEmpty();
		assertThat(profile.filtros().presupuestoMinCop()).isEqualTo(30_000_000L);
	}

}
