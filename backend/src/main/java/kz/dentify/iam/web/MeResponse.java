package kz.dentify.iam.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import kz.dentify.clinic.api.ClinicDirectory.BranchInfo;
import kz.dentify.clinic.api.ClinicDirectory.ClinicInfo;
import kz.dentify.iam.internal.AccountOverview;
import kz.dentify.iam.internal.AccountOverview.ClinicAccess;
import kz.dentify.iam.internal.AccountOverview.Context;
import kz.dentify.iam.internal.AccountOverview.Profile;
import kz.dentify.iam.internal.MembershipStatus;
import org.jspecify.annotations.Nullable;

/**
 * Вошедший аккаунт (IAM-08): аккаунт, членства во всех клиниках и клиника с филиалом сессии. По нему интерфейс решает,
 * открыть расписание или выбор клиники. Роль и права — с каталогом прав (T-IAM-06, T-IAM-07), фото аккаунта — со ссылками
 * файлового хранилища (T-PLT-20) и API профиля (T-IAM-24): поля добавятся, не ломая ответ.
 *
 * @param account аккаунт
 * @param memberships членства во всех клиниках
 * @param context клиника и филиал сессии; null — клиника не выбрана, интерфейс показывает выбор клиники
 */
@Schema(description = "Вошедший аккаунт, его клиники и клиника сессии")
record MeResponse(@Schema(description = "Аккаунт") MeAccount account,
		@Schema(description = "Членства во всех клиниках, в порядке создания, включая отключённые и приглашения")
		List<MeMembership> memberships,
		@Schema(description = "Клиника и филиал сессии; null — клиника не выбрана") @Nullable MeContext context) {

	static MeResponse from(AccountOverview overview) {
		Context context = overview.context();
		return new MeResponse(MeAccount.from(overview.account()),
				overview.memberships().stream().map(MeMembership::from).toList(),
				(context != null) ? MeContext.from(context) : null);
	}

	/**
	 * Аккаунт.
	 *
	 * @param id аккаунт
	 * @param fullName ФИО
	 * @param initials инициалы для аватара
	 * @param language язык интерфейса
	 * @param phone телефон в E.164
	 * @param email e-mail
	 */
	@Schema(description = "Аккаунт")
	record MeAccount(@Schema(description = "Аккаунт") UUID id,
			@Schema(description = "ФИО", example = "Алиева Динара") String fullName,
			@Schema(description = "Инициалы для аватара: первые буквы двух первых слов ФИО", example = "АД") String initials,
			@Schema(description = "Язык интерфейса", allowableValues = { "ru", "kk" }, example = "ru") String language,
			@Schema(description = "Телефон в E.164; null — нет", example = "+77071234567") @Nullable String phone,
			@Schema(description = "E-mail; null — нет", example = "dinara@example.kz") @Nullable String email) {

		static MeAccount from(Profile profile) {
			return new MeAccount(profile.id(), profile.fullName(), profile.initials(), profile.language().getLanguage(),
					(profile.phone() != null) ? profile.phone().value() : null,
					(profile.email() != null) ? profile.email().value() : null);
		}

	}

	/**
	 * Членство в клинике.
	 *
	 * @param id членство
	 * @param status статус
	 * @param clinic клиника
	 * @param branches филиалы, в которые можно войти
	 */
	@Schema(description = "Членство в клинике")
	record MeMembership(@Schema(description = "Членство") UUID id,
			@Schema(description = "ACTIVE — можно войти; DISABLED — доступ отключён; INVITED — приглашение не принято")
			MembershipStatus status, @Schema(description = "Клиника") ClinicRef clinic,
			@Schema(description = "Филиалы, в которые можно войти; у отключённого членства и приглашения — пусто")
			List<BranchRef> branches) {

		static MeMembership from(ClinicAccess access) {
			return new MeMembership(access.membershipId(), access.status(), ClinicRef.from(access.clinic()),
					access.branches().stream().map(BranchRef::from).toList());
		}

	}

	/**
	 * Клиника и филиал сессии.
	 *
	 * @param clinic клиника
	 * @param branch филиал или null
	 */
	@Schema(description = "Клиника и филиал сессии")
	record MeContext(@Schema(description = "Клиника") ClinicRef clinic,
			@Schema(description = "Филиал; null — не выбран") @Nullable BranchRef branch) {

		static MeContext from(Context context) {
			BranchInfo branch = context.branch();
			return new MeContext(ClinicRef.from(context.clinic()), (branch != null) ? BranchRef.from(branch) : null);
		}

	}

	/**
	 * Клиника.
	 *
	 * @param id клиника
	 * @param name название
	 * @param city город
	 */
	@Schema(description = "Клиника")
	record ClinicRef(@Schema(description = "Клиника") UUID id,
			@Schema(description = "Название", example = "Dentify Demo") String name,
			@Schema(description = "Город", example = "Алматы") String city) {

		static ClinicRef from(ClinicInfo clinic) {
			return new ClinicRef(clinic.id(), clinic.name(), clinic.city());
		}

	}

	/**
	 * Филиал.
	 *
	 * @param id филиал
	 * @param name название
	 * @param city город
	 */
	@Schema(description = "Филиал клиники")
	record BranchRef(@Schema(description = "Филиал") UUID id,
			@Schema(description = "Название", example = "Абая, 150") String name,
			@Schema(description = "Город", example = "Алматы") String city) {

		static BranchRef from(BranchInfo branch) {
			return new BranchRef(branch.id(), branch.name(), branch.city());
		}

	}

}
