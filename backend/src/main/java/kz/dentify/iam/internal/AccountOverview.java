package kz.dentify.iam.internal;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import kz.dentify.clinic.api.ClinicDirectory.BranchInfo;
import kz.dentify.clinic.api.ClinicDirectory.ClinicInfo;
import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.PhoneNumber;
import org.jspecify.annotations.Nullable;

/**
 * Вошедший аккаунт для интерфейса ({@code GET /api/v1/me}, IAM-08): аккаунт, его членства во всех клиниках и текущий
 * контекст — клиника и филиал сессии. Роль и права появятся с каталогом прав (T-IAM-06, T-IAM-07), фото аккаунта
 * ({@code account.photo_key}) — со ссылками файлового хранилища (T-PLT-20) и API профиля (T-IAM-24).
 *
 * @param account аккаунт
 * @param memberships членства во всех клиниках, в порядке создания, в том числе отключённые и приглашения
 * @param context клиника и филиал сессии или null, если клиника не выбрана
 */
public record AccountOverview(Profile account, List<ClinicAccess> memberships, @Nullable Context context) {

	public AccountOverview {
		memberships = List.copyOf(memberships);
	}

	/**
	 * Аккаунт.
	 *
	 * @param id аккаунт
	 * @param fullName ФИО
	 * @param initials инициалы для аватара: первые буквы первых двух слов ФИО
	 * @param language язык интерфейса
	 * @param phone телефон или null
	 * @param email e-mail или null
	 */
	public record Profile(UUID id, String fullName, String initials, Locale language, @Nullable PhoneNumber phone,
			@Nullable EmailAddress email) {
	}

	/**
	 * Членство в клинике.
	 *
	 * @param membershipId членство
	 * @param status статус членства
	 * @param clinic клиника без филиалов
	 * @param branches филиалы, в которые можно войти: у активного членства — доступные ему, у остальных — пусто
	 */
	public record ClinicAccess(UUID membershipId, MembershipStatus status, ClinicInfo clinic, List<BranchInfo> branches) {

		public ClinicAccess {
			branches = List.copyOf(branches);
		}

	}

	/**
	 * Клиника и филиал сессии.
	 *
	 * @param clinic клиника без филиалов
	 * @param branch выбранный филиал или null
	 */
	public record Context(ClinicInfo clinic, @Nullable BranchInfo branch) {
	}

	/** Первые буквы первых двух слов ФИО заглавными: Динара Алиева — ДА. */
	static String initials(String fullName) {
		StringBuilder initials = new StringBuilder();
		for (String word : fullName.strip().split("\\s+", 3)) {
			if (initials.codePointCount(0, initials.length()) == 2) {
				break;
			}
			if (!word.isEmpty()) {
				initials.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
			}
		}
		return initials.toString();
	}

}
