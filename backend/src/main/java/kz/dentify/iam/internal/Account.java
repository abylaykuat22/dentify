package kz.dentify.iam.internal;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.GlobalEntity;
import kz.dentify.platform.api.Languages;
import kz.dentify.platform.api.PhoneNumber;
import org.jspecify.annotations.Nullable;

/**
 * Аккаунт — один на человека для всех клиник (IAM-01): телефон и e-mail для входа (хотя бы один, оба уникальны во
 * всём Dentify), ФИО, хэш пароля, язык интерфейса, фото, статус. Глобальная таблица без клиники: доступ к клинике —
 * {@link Membership}.
 * <p>
 * Телефон и e-mail попадают сюда только нормализованными: {@link PhoneNumber} в E.164, {@link EmailAddress} в нижнем
 * регистре. Хэш пароля — только от бина {@code PasswordEncoder} ({@code {argon2}…}); сам пароль сущность не видит.
 * Создаёт аккаунт {@link Accounts#create}.
 */
@Entity
@Table(name = "account")
public class Account extends GlobalEntity {

	@Column(name = "phone")
	private @Nullable String phone;

	@Column(name = "email")
	private @Nullable String email;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "language", nullable = false)
	private String language;

	@Column(name = "photo_key")
	private @Nullable String photoKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private AccountStatus status;

	protected Account() {
		this.fullName = "";
		this.passwordHash = "";
		this.language = Languages.RUSSIAN.getLanguage();
		this.status = AccountStatus.ACTIVE;
	}

	Account(@Nullable PhoneNumber phone, @Nullable EmailAddress email, String fullName, String passwordHash) {
		if (phone == null && email == null) {
			throw new IllegalArgumentException("У аккаунта нет ни телефона, ни e-mail");
		}
		this.phone = (phone != null) ? phone.value() : null;
		this.email = (email != null) ? email.value() : null;
		this.fullName = fullName;
		this.passwordHash = passwordHash;
		this.language = Languages.RUSSIAN.getLanguage();
		this.status = AccountStatus.ACTIVE;
	}

	/** Телефон в E.164 или null. */
	public @Nullable PhoneNumber getPhone() {
		return (this.phone != null) ? new PhoneNumber(this.phone) : null;
	}

	/** E-mail в нижнем регистре или null. */
	public @Nullable EmailAddress getEmail() {
		return (this.email != null) ? new EmailAddress(this.email) : null;
	}

	public String getFullName() {
		return this.fullName;
	}

	/** Хэш пароля для {@code PasswordEncoder.matches}; наружу не отдаётся. */
	String getPasswordHash() {
		return this.passwordHash;
	}

	/** Язык интерфейса аккаунта: {@code ru} или {@code kk}. */
	public Locale getLanguage() {
		return Locale.of(this.language);
	}

	/** Ключ фото в хранилище файлов или null (T-IAM-24). */
	public @Nullable String getPhotoKey() {
		return this.photoKey;
	}

	public AccountStatus getStatus() {
		return this.status;
	}

}
