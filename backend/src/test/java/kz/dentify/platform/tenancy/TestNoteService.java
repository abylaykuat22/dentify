package kz.dentify.platform.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import kz.dentify.platform.api.AuditActions;
import kz.dentify.platform.api.Audited;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.TenantContext;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Пробный сервис данных клиники, только для тестов: так же, как сервис модуля, находит объект в своей клинике и
 * отвечает 404 на чужой. Изменения пишет в журнал действий аннотацией {@link Audited} со снимками
 * {@link TestNoteSnapshots}.
 */
@Service
@Transactional
class TestNoteService {

	private final TestNoteRepository notes;

	private final JdbcTemplate jdbc;

	TestNoteService(TestNoteRepository notes, JdbcTemplate jdbc) {
		this.notes = notes;
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	List<TestNote> list() {
		return this.notes.findAll();
	}

	@Audited(entity = TestNoteSnapshots.ENTITY, action = AuditActions.CREATED, id = Audited.RESULT_ID)
	TestNote create(String title) {
		return this.notes.save(new TestNote(title, TenantContext.branchId()));
	}

	@Transactional(readOnly = true)
	TestNote get(UUID id) {
		return find(id);
	}

	@Audited(entity = TestNoteSnapshots.ENTITY, action = AuditActions.UPDATED)
	TestNote rename(UUID id, String title, long version) {
		TestNote note = find(id);
		note.checkVersion(version);
		note.rename(title);
		return this.notes.saveAndFlush(note);
	}

	@Audited(entity = TestNoteSnapshots.ENTITY, action = AuditActions.ARCHIVED)
	TestNote archive(UUID id, long version) {
		TestNote note = find(id);
		note.checkVersion(version);
		note.archive();
		return this.notes.saveAndFlush(note);
	}

	@Audited(entity = TestNoteSnapshots.ENTITY, action = AuditActions.DELETED)
	void delete(UUID id) {
		this.notes.delete(find(id));
	}

	/** В обход Hibernate: условия по клинике в SQL нет, чужую строку скрывает только RLS. */
	@Transactional(readOnly = true)
	Optional<String> titleViaSql(UUID id) {
		return this.jdbc.queryForList("select title from test_note where id = ?", String.class, id).stream().findFirst();
	}

	/** Второй объект по вложенному пути: оба должны быть в клинике запроса, иначе 404. */
	@Transactional(readOnly = true)
	TestNote related(UUID id, UUID relatedId) {
		find(id);
		return find(relatedId);
	}

	/**
	 * Область {@code SUPPORTS} без внешней транзакции: настоящей транзакции нет, но синхронизация Spring включена, и
	 * после первого обращения к данным сессия Hibernate привязана к потоку до конца метода.
	 */
	@Transactional(propagation = Propagation.SUPPORTS)
	<T> T inSupportsScope(Supplier<T> action) {
		return action.get();
	}

	private TestNote find(UUID id) {
		return this.notes.findById(id).orElseThrow(NotFoundException::new);
	}

}
