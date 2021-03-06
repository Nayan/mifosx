/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.savings.domain;

import static org.mifosplatform.portfolio.savings.SavingsApiConstants.SAVINGS_ACCOUNT_RESOURCE_NAME;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.annualFeeAmountParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.annualFeeOnMonthDayParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.localeParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.lockinPeriodFrequencyParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.lockinPeriodFrequencyTypeParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.withdrawalFeeAmountParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.withdrawalFeeTypeParamName;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.joda.time.LocalDate;
import org.joda.time.MonthDay;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.ApiParameterError;
import org.mifosplatform.infrastructure.core.data.DataValidatorBuilder;
import org.mifosplatform.infrastructure.core.domain.LocalDateInterval;
import org.mifosplatform.infrastructure.core.exception.PlatformApiDataValidationException;
import org.mifosplatform.infrastructure.core.service.DateUtils;
import org.mifosplatform.infrastructure.security.service.RandomPasswordGenerator;
import org.mifosplatform.organisation.monetary.data.CurrencyData;
import org.mifosplatform.organisation.monetary.domain.MonetaryCurrency;
import org.mifosplatform.organisation.monetary.domain.Money;
import org.mifosplatform.organisation.staff.domain.Staff;
import org.mifosplatform.portfolio.client.domain.Client;
import org.mifosplatform.portfolio.group.domain.Group;
import org.mifosplatform.portfolio.loanproduct.domain.PeriodFrequencyType;
import org.mifosplatform.portfolio.paymentdetail.domain.PaymentDetail;
import org.mifosplatform.portfolio.savings.SavingsApiConstants;
import org.mifosplatform.portfolio.savings.SavingsCompoundingInterestPeriodType;
import org.mifosplatform.portfolio.savings.SavingsInterestCalculationDaysInYearType;
import org.mifosplatform.portfolio.savings.SavingsInterestCalculationType;
import org.mifosplatform.portfolio.savings.SavingsPeriodFrequencyType;
import org.mifosplatform.portfolio.savings.SavingsPostingInterestPeriodType;
import org.mifosplatform.portfolio.savings.SavingsWithdrawalFeesType;
import org.mifosplatform.portfolio.savings.domain.interest.PostingPeriod;
import org.mifosplatform.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.mifosplatform.portfolio.savings.service.SavingsEnumerations;
import org.mifosplatform.useradministration.domain.AppUser;
import org.springframework.data.jpa.domain.AbstractPersistable;

@Entity
@Table(name = "m_savings_account", uniqueConstraints = { @UniqueConstraint(columnNames = { "account_no" }, name = "sa_account_no_UNIQUE"),
        @UniqueConstraint(columnNames = { "external_id" }, name = "sa_external_id_UNIQUE") })
public class SavingsAccount extends AbstractPersistable<Long> {

    @Column(name = "account_no", length = 20, unique = true, nullable = false)
    private String accountNumber;

    @Column(name = "external_id", nullable = true)
    private String externalId;

    @ManyToOne(optional = true)
    @JoinColumn(name = "client_id", nullable = true)
    private Client client;

    @ManyToOne(optional = true)
    @JoinColumn(name = "group_id", nullable = true)
    private Group group;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private SavingsProduct product;

    @ManyToOne
    @JoinColumn(name = "field_officer_id", nullable = true)
    private Staff fieldOfficer;

    @Column(name = "status_enum", nullable = false)
    private Integer status;

    @Temporal(TemporalType.DATE)
    @Column(name = "submittedon_date", nullable = true)
    private Date submittedOnDate;

    @SuppressWarnings("unused")
    @ManyToOne(optional = true)
    @JoinColumn(name = "submittedon_userid", nullable = true)
    private AppUser submittedBy;

    @SuppressWarnings("unused")
    @Temporal(TemporalType.DATE)
    @Column(name = "rejectedon_date")
    private Date rejectedOnDate;

    @SuppressWarnings("unused")
    @ManyToOne(optional = true)
    @JoinColumn(name = "rejectedon_userid", nullable = true)
    private AppUser rejectedBy;

    @SuppressWarnings("unused")
    @Temporal(TemporalType.DATE)
    @Column(name = "withdrawnon_date")
    private Date withdrawnOnDate;

    @SuppressWarnings("unused")
    @ManyToOne(optional = true)
    @JoinColumn(name = "withdrawnon_userid", nullable = true)
    private AppUser withdrawnBy;

    @Temporal(TemporalType.DATE)
    @Column(name = "approvedon_date")
    private Date approvedOnDate;

    @SuppressWarnings("unused")
    @ManyToOne(optional = true)
    @JoinColumn(name = "approvedon_userid", nullable = true)
    private AppUser approvedBy;

    @Temporal(TemporalType.DATE)
    @Column(name = "activatedon_date", nullable = true)
    private Date activatedOnDate;

    @SuppressWarnings("unused")
    @ManyToOne(optional = true)
    @JoinColumn(name = "activatedon_userid", nullable = true)
    private AppUser activatedBy;

    @SuppressWarnings("unused")
    @Temporal(TemporalType.DATE)
    @Column(name = "closedon_date")
    private Date closedOnDate;

    @SuppressWarnings("unused")
    @ManyToOne(optional = true)
    @JoinColumn(name = "closedon_userid", nullable = true)
    private AppUser closedBy;

    @Embedded
    private MonetaryCurrency currency;

    @Column(name = "nominal_annual_interest_rate", scale = 6, precision = 19, nullable = false)
    private BigDecimal nominalAnnualInterestRate;

    /**
     * The interest period is the span of time at the end of which savings in a
     * client's account earn interest.
     * 
     * A value from the {@link SavingsCompoundingInterestPeriodType}
     * enumeration.
     */
    @Column(name = "interest_compounding_period_enum", nullable = false)
    private Integer interestCompoundingPeriodType;

    /**
     * A value from the {@link SavingsPostingInterestPeriodType} enumeration.
     */
    @Column(name = "interest_posting_period_enum", nullable = false)
    private Integer interestPostingPeriodType;

    /**
     * A value from the {@link SavingsInterestCalculationType} enumeration.
     */
    @Column(name = "interest_calculation_type_enum", nullable = false)
    private Integer interestCalculationType;

    /**
     * A value from the {@link SavingsInterestCalculationDaysInYearType}
     * enumeration.
     */
    @Column(name = "interest_calculation_days_in_year_type_enum", nullable = false)
    private Integer interestCalculationDaysInYearType;

    @Column(name = "min_required_opening_balance", scale = 6, precision = 19, nullable = true)
    private BigDecimal minRequiredOpeningBalance;

    @Column(name = "lockin_period_frequency", nullable = true)
    private Integer lockinPeriodFrequency;

    @Column(name = "lockin_period_frequency_enum", nullable = true)
    private Integer lockinPeriodFrequencyType;

    /**
     * When account becomes <code>active</code> this field is derived if
     * <code>lockinPeriodFrequency</code> and
     * <code>lockinPeriodFrequencyType</code> details are present.
     */
    @Temporal(TemporalType.DATE)
    @Column(name = "lockedin_until_date_derived", nullable = true)
    private Date lockedInUntilDate;

    @Column(name = "withdrawal_fee_amount", scale = 6, precision = 19, nullable = true)
    private BigDecimal withdrawalFeeAmount;

    @Column(name = "withdrawal_fee_type_enum", nullable = true)
    private Integer withdrawalFeeType;

    @Column(name = "annual_fee_amount", scale = 6, precision = 19, nullable = true)
    private BigDecimal annualFeeAmount;

    @Column(name = "annual_fee_on_month", nullable = true)
    private Integer annualFeeOnMonth;

    @Column(name = "annual_fee_on_day", nullable = true)
    private Integer annualFeeOnDay;

    @SuppressWarnings("unused")
    @Temporal(TemporalType.DATE)
    @Column(name = "annual_fee_next_due_date", nullable = true)
    private Date annualFeeNextDueDate;

    @Embedded
    private SavingsAccountSummary summary;

    @OrderBy(value = "dateOf, id")
    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "savingsAccount", orphanRemoval = true)
    private final List<SavingsAccountTransaction> transactions = new ArrayList<SavingsAccountTransaction>();

    @Transient
    private boolean accountNumberRequiresAutoGeneration = false;
    @Transient
    private SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper;
    @Transient
    private SavingsHelper savingsHelper;

    protected SavingsAccount() {
        //
    }

    public static SavingsAccount createNewApplicationForSubmittal(final Client client, final Group group, final SavingsProduct product,
            final Staff fieldOfficer, final String accountNo, final String externalId, final LocalDate submittedOnDate,
            final BigDecimal interestRate, final SavingsCompoundingInterestPeriodType interestCompoundingPeriodType,
            final SavingsPostingInterestPeriodType interestPostingPeriodType, final SavingsInterestCalculationType interestCalculationType,
            final SavingsInterestCalculationDaysInYearType interestCalculationDaysInYearType, final BigDecimal minRequiredOpeningBalance,
            final Integer lockinPeriodFrequency, final SavingsPeriodFrequencyType lockinPeriodFrequencyType,
            final BigDecimal withdrawalFeeAmount, final SavingsWithdrawalFeesType withdrawalFeeType, final BigDecimal annualFeeAmount,
            final MonthDay annualFeeOnMonthDay) {

        final SavingsAccountStatusType status = SavingsAccountStatusType.SUBMITTED_AND_PENDING_APPROVAL;
        return new SavingsAccount(client, group, product, fieldOfficer, accountNo, externalId, status, submittedOnDate, interestRate,
                interestCompoundingPeriodType, interestPostingPeriodType, interestCalculationType, interestCalculationDaysInYearType,
                minRequiredOpeningBalance, lockinPeriodFrequency, lockinPeriodFrequencyType, withdrawalFeeAmount, withdrawalFeeType,
                annualFeeAmount, annualFeeOnMonthDay);
    }

    private SavingsAccount(final Client client, final Group group, final SavingsProduct product, final Staff fieldOfficer,
            final String accountNo, final String externalId, final SavingsAccountStatusType status, final LocalDate submittedOnDate,
            final BigDecimal nominalAnnualInterestRate, final SavingsCompoundingInterestPeriodType interestCompoundingPeriodType,
            final SavingsPostingInterestPeriodType interestPostingPeriodType, final SavingsInterestCalculationType interestCalculationType,
            final SavingsInterestCalculationDaysInYearType interestCalculationDaysInYearType, final BigDecimal minRequiredOpeningBalance,
            final Integer lockinPeriodFrequency, final SavingsPeriodFrequencyType lockinPeriodFrequencyType,
            final BigDecimal withdrawalFeeAmount, final SavingsWithdrawalFeesType withdrawalFeeType, final BigDecimal annualFeeAmount,
            final MonthDay annualFeeOnMonthDay) {
        this.client = client;
        this.group = group;
        this.product = product;
        this.fieldOfficer = fieldOfficer;
        if (StringUtils.isBlank(accountNo)) {
            this.accountNumber = new RandomPasswordGenerator(19).generate();
            this.accountNumberRequiresAutoGeneration = true;
        } else {
            this.accountNumber = accountNo;
        }
        this.currency = product.currency();
        this.externalId = externalId;
        this.status = status.getValue();
        this.submittedOnDate = submittedOnDate.toDate();
        this.nominalAnnualInterestRate = nominalAnnualInterestRate;
        this.interestCompoundingPeriodType = interestCompoundingPeriodType.getValue();
        this.interestPostingPeriodType = interestPostingPeriodType.getValue();
        this.interestCalculationType = interestCalculationType.getValue();
        this.interestCalculationDaysInYearType = interestCalculationDaysInYearType.getValue();
        this.minRequiredOpeningBalance = minRequiredOpeningBalance;
        this.lockinPeriodFrequency = lockinPeriodFrequency;
        if (lockinPeriodFrequencyType != null) {
            this.lockinPeriodFrequencyType = lockinPeriodFrequencyType.getValue();
        }
        this.withdrawalFeeAmount = withdrawalFeeAmount;
        if (withdrawalFeeType != null) {
            this.withdrawalFeeType = withdrawalFeeType.getValue();
        }

        this.annualFeeAmount = annualFeeAmount;
        if (annualFeeOnMonthDay != null) {
            this.annualFeeOnMonth = annualFeeOnMonthDay.getMonthOfYear();
            this.annualFeeOnDay = annualFeeOnMonthDay.getDayOfMonth();

            updateAnnualFeeNextDueDate();
        }

        this.summary = new SavingsAccountSummary();
    }

    private void updateAnnualFeeNextDueDate() {
        LocalDate nextDueLocalDate = new LocalDate().withMonthOfYear(this.annualFeeOnMonth).withDayOfMonth(this.annualFeeOnDay);
        if (nextDueLocalDate.isBefore(DateUtils.getLocalDateOfTenant())) {
            nextDueLocalDate = nextDueLocalDate.plusYears(1);
        }

        this.annualFeeNextDueDate = nextDueLocalDate.toDate();
    }

    /**
     * Used after fetching/hydrating a {@link SavingsAccount} object to inject
     * helper services/components used for update summary details after
     * events/transactions on a {@link SavingsAccount}.
     */
    public void setHelpers(final SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper,
            final SavingsHelper savingsHelper) {
        this.savingsAccountTransactionSummaryWrapper = savingsAccountTransactionSummaryWrapper;
        this.savingsHelper = savingsHelper;
    }

    public boolean isNotActive() {
        return !isActive();
    }

    public boolean isActive() {
        return SavingsAccountStatusType.fromInt(this.status).isActive();
    }

    public boolean isNotSubmittedAndPendingApproval() {
        return !isSubmittedAndPendingApproval();
    }

    public boolean isSubmittedAndPendingApproval() {
        return SavingsAccountStatusType.fromInt(this.status).isSubmittedAndPendingApproval();
    }

    public void postInterest(final MathContext mc, final LocalDate interestPostingUpToDate, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds) {

        final List<PostingPeriod> postingPeriods = calculateInterestUsing(mc, interestPostingUpToDate);

        Money interestPostedToDate = Money.zero(this.currency);

        boolean recalucateDailyBalanceDetails = false;
        existingTransactionIds.addAll(findExistingTransactionIds());
        existingReversedTransactionIds.addAll(findExistingReversedTransactionIds());

        for (PostingPeriod interestPostingPeriod : postingPeriods) {

            final LocalDate interestPostingTransactionDate = interestPostingPeriod.dateOfPostingTransaction();
            final Money interestEarnedToBePostedForPeriod = interestPostingPeriod.getInterestEarned();

            if (!interestPostingTransactionDate.isAfter(interestPostingUpToDate)) {

                interestPostedToDate = interestPostedToDate.plus(interestEarnedToBePostedForPeriod);

                SavingsAccountTransaction postingTransaction = findInterestPostingTransactionFor(interestPostingTransactionDate);
                if (postingTransaction == null) {
                    final SavingsAccountTransaction newPostingTransaction = SavingsAccountTransaction.interestPosting(this,
                            interestPostingTransactionDate, interestEarnedToBePostedForPeriod);
                    this.transactions.add(newPostingTransaction);
                    recalucateDailyBalanceDetails = true;
                } else {
                    boolean correctionRequired = postingTransaction.hasNotAmount(interestEarnedToBePostedForPeriod);
                    if (correctionRequired) {
                        postingTransaction.reverse();
                        final SavingsAccountTransaction newPostingTransaction = SavingsAccountTransaction.interestPosting(this,
                                interestPostingTransactionDate, interestEarnedToBePostedForPeriod);
                        this.transactions.add(newPostingTransaction);
                        recalucateDailyBalanceDetails = true;
                    }
                }
            }
        }

        if (recalucateDailyBalanceDetails) {
            // no openingBalance concept supported yet but probably will to
            // allow
            // for migrations.
            final Money openingAccountBalance = Money.zero(this.currency);

            // update existing transactions so derived balance fields are
            // correct.
            recalculateDailyBalances(openingAccountBalance);
        }

        this.summary.updateSummary(this.currency, this.savingsAccountTransactionSummaryWrapper, this.transactions);
    }

    private SavingsAccountTransaction findInterestPostingTransactionFor(final LocalDate postingDate) {

        SavingsAccountTransaction postingTransation = null;

        for (SavingsAccountTransaction transaction : this.transactions) {
            if (transaction.isInterestPosting() && transaction.occursOn(postingDate)) {
                postingTransation = transaction;
                break;
            }
        }

        return postingTransation;
    }

    /**
     * All interest calculation based on END-OF-DAY-BALANCE.
     * 
     * Interest calculation is performed on-the-fly over all account
     * transactions.
     * 
     * 
     * 1. Calculate Interest From Beginning Of Account 1a. determine the
     * 'crediting' periods that exist for this savings acccount 1b. determine
     * the 'compounding' periods that exist within each 'crediting' period
     * calculate the amount of interest due at the end of each 'crediting'
     * period check if an existing 'interest posting' transaction exists for
     * date and matches the amount posted
     */
    public List<PostingPeriod> calculateInterestUsing(final MathContext mc, final LocalDate upToInterestCalculationDate) {

        // no openingBalance concept supported yet but probably will to allow
        // for migrations.
        final Money openingAccountBalance = Money.zero(this.currency);

        // update existing transactions so derived balance fields are
        // correct.
        recalculateDailyBalances(openingAccountBalance);

        // 1. default to calculate interest based on entire history OR
        // 2. determine latest 'posting period' and find interest credited to
        // that period

        // A generate list of EndOfDayBalances (not including interest postings)
        final SavingsPostingInterestPeriodType postingPeriodType = SavingsPostingInterestPeriodType.fromInt(this.interestPostingPeriodType);

        final SavingsCompoundingInterestPeriodType compoundingPeriodType = SavingsCompoundingInterestPeriodType
                .fromInt(this.interestCompoundingPeriodType);

        final SavingsInterestCalculationDaysInYearType daysInYearType = SavingsInterestCalculationDaysInYearType
                .fromInt(this.interestCalculationDaysInYearType);

        List<LocalDateInterval> postingPeriodIntervals = savingsHelper.determineInterestPostingPeriods(getActivationLocalDate(),
                upToInterestCalculationDate, postingPeriodType);

        List<PostingPeriod> allPostingPeriods = new ArrayList<PostingPeriod>();

        Money periodStartingBalance = Money.zero(this.currency);

        final SavingsInterestCalculationType interestCalculationType = SavingsInterestCalculationType.fromInt(this.interestCalculationType);
        BigDecimal interestRateAsFraction = nominalAnnualInterestRate.divide(BigDecimal.valueOf(100l), mc);
        for (LocalDateInterval periodInterval : postingPeriodIntervals) {

            PostingPeriod postingPeriod = PostingPeriod.createFrom(periodInterval, periodStartingBalance,
                    retreiveOrderedListOfTransactions(), currency, compoundingPeriodType, interestCalculationType, interestRateAsFraction,
                    daysInYearType.getValue());

            periodStartingBalance = postingPeriod.closingBalance();

            allPostingPeriods.add(postingPeriod);
        }

        savingsHelper.calculateInterestForAllPostingPeriods(currency, allPostingPeriods);

        this.summary.updateFromInterestPeriodSummaries(currency, allPostingPeriods);
        this.summary.updateSummary(this.currency, this.savingsAccountTransactionSummaryWrapper, this.transactions);

        return allPostingPeriods;
    }

    private List<SavingsAccountTransaction> retreiveOrderedListOfTransactions() {
        List<SavingsAccountTransaction> listOfTransactionsSorted = retreiveListOfTransactions();

        List<SavingsAccountTransaction> orderedNonInterestPostingTransactions = new ArrayList<SavingsAccountTransaction>();

        for (SavingsAccountTransaction transaction : listOfTransactionsSorted) {
            if (!transaction.isInterestPosting() && transaction.isNotReversed()) {
                orderedNonInterestPostingTransactions.add(transaction);
            }
        }

        return orderedNonInterestPostingTransactions;
    }

    private List<SavingsAccountTransaction> retreiveListOfTransactions() {
        List<SavingsAccountTransaction> listOfTransactionsSorted = new ArrayList<SavingsAccountTransaction>();
        for (SavingsAccountTransaction transaction : this.transactions) {
            listOfTransactionsSorted.add(transaction);
        }

        SavingsAccountTransactionComparator transactionComparator = new SavingsAccountTransactionComparator();
        Collections.sort(listOfTransactionsSorted, transactionComparator);

        return listOfTransactionsSorted;
    }

    private void recalculateDailyBalances(final Money openingAccountBalance) {

        Money runningBalance = openingAccountBalance.copy();

        List<SavingsAccountTransaction> accountTransactionsSorted = retreiveListOfTransactions();

        for (SavingsAccountTransaction transaction : accountTransactionsSorted) {
            if (transaction.isReversed()) {
                transaction.zeroBalanceFields();
            } else {
                Money transactionAmount = Money.zero(this.currency);
                if (transaction.isDeposit()) {
                    transactionAmount = transactionAmount.plus(transaction.getAmount(this.currency));
                } else if (transaction.isWithdrawal()) {
                    transactionAmount = transactionAmount.minus(transaction.getAmount(this.currency));
                } else if (transaction.isInterestPosting()) {
                    transactionAmount = transactionAmount.plus(transaction.getAmount(this.currency));
                }

                runningBalance = runningBalance.plus(transactionAmount);
                transaction.updateRunningBalance(runningBalance);
            }
        }

        // loop over transactions in reverse
        LocalDate endOfBalanceDate = DateUtils.getLocalDateOfTenant();
        for (int i = accountTransactionsSorted.size() - 1; i >= 0; i--) {
            SavingsAccountTransaction transaction = accountTransactionsSorted.get(i);
            if (transaction.isNotReversed()) {
                transaction.updateCumulativeBalanceAndDates(this.currency, endOfBalanceDate);

                // this transactions transaction date is end of balance date for
                // previous transaction.
                endOfBalanceDate = transaction.transactionLocalDate().minusDays(1);
            }
        }
    }

    public SavingsAccountTransaction deposit(final DateTimeFormatter formatter, final LocalDate transactionDate,
            final BigDecimal transactionAmount, final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds,
            final PaymentDetail paymentDetail) {

        if (isNotActive()) {
            final String defaultUserMessage = "Transaction is not allowed. Account is not active.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.account.is.not.active",
                    defaultUserMessage, "transactionDate", transactionDate.toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (isDateInTheFuture(transactionDate)) {
            final String defaultUserMessage = "Transaction date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.in.the.future",
                    defaultUserMessage, "transactionDate", transactionDate.toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (transactionDate.isBefore(getActivationLocalDate())) {
            final Object[] defaultUserArgs = Arrays.asList(transactionDate.toString(formatter),
                    getActivationLocalDate().toString(formatter)).toArray();
            final String defaultUserMessage = "Transaction date cannot be before accounts activation date.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.before.activation.date",
                    defaultUserMessage, "transactionDate", defaultUserArgs);

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        existingTransactionIds.addAll(findExistingTransactionIds());
        existingReversedTransactionIds.addAll(findExistingReversedTransactionIds());

        final Money amount = Money.of(this.currency, transactionAmount);

        final SavingsAccountTransaction transaction = SavingsAccountTransaction.deposit(this, paymentDetail, transactionDate, amount);
        this.transactions.add(transaction);

        this.summary.updateSummary(this.currency, this.savingsAccountTransactionSummaryWrapper, this.transactions);

        return transaction;
    }

    private LocalDate getActivationLocalDate() {
        LocalDate activationLocalDate = null;
        if (this.activatedOnDate != null) {
            activationLocalDate = new LocalDate(this.activatedOnDate);
        }
        return activationLocalDate;
    }

    public SavingsAccountTransaction withdraw(final DateTimeFormatter formatter, final LocalDate transactionDate,
            final BigDecimal transactionAmount, final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds,
            final PaymentDetail paymentDetail) {

        if (isNotActive()) {

            final String defaultUserMessage = "Transaction is not allowed. Account is not active.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.account.is.not.active",
                    defaultUserMessage, "transactionDate", transactionDate.toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (isDateInTheFuture(transactionDate)) {
            final String defaultUserMessage = "Transaction date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.in.the.future",
                    defaultUserMessage, "transactionDate", transactionDate.toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (transactionDate.isBefore(getActivationLocalDate())) {
            final Object[] defaultUserArgs = Arrays.asList(transactionDate.toString(formatter),
                    getActivationLocalDate().toString(formatter)).toArray();
            final String defaultUserMessage = "Transaction date cannot be before accounts activation date.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.before.activation.date",
                    defaultUserMessage, "transactionDate", defaultUserArgs);

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (isAccountLocked(transactionDate)) {
            final String defaultUserMessage = "Withdrawal is not allowed. No withdrawals are allowed until after "
                    + getLockedInUntilLocalDate().toString(formatter);
            final ApiParameterError error = ApiParameterError.parameterError(
                    "error.msg.savingsaccount.transaction.withdrawals.blocked.during.lockin.period", defaultUserMessage, "transactionDate",
                    transactionDate.toString(formatter), getLockedInUntilLocalDate().toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        existingTransactionIds.addAll(findExistingTransactionIds());
        existingReversedTransactionIds.addAll(findExistingReversedTransactionIds());

        final Money transactionAmountMoney = Money.of(this.currency, transactionAmount);
        final SavingsAccountTransaction transaction = SavingsAccountTransaction.withdrawal(this, paymentDetail, transactionDate,
                transactionAmountMoney);
        this.transactions.add(transaction);

        if (isAutomaticWithdrawalFee()) {

            SavingsAccountTransaction withdrawalFeeTransaction = null;
            Money feeAmount = Money.zero(this.currency);
            switch (SavingsWithdrawalFeesType.fromInt(this.withdrawalFeeType)) {
                case INVALID:
                break;
                case FLAT:
                    feeAmount = Money.of(this.currency, this.withdrawalFeeAmount);
                    withdrawalFeeTransaction = SavingsAccountTransaction.fee(this, transactionDate, feeAmount);
                    this.transactions.add(withdrawalFeeTransaction);
                break;
                case PERCENT_OF_AMOUNT:
                    final BigDecimal feeAmountDecimal = transactionAmount.multiply(this.withdrawalFeeAmount).divide(
                            BigDecimal.valueOf(100l));
                    feeAmount = Money.of(this.currency, feeAmountDecimal);
                    withdrawalFeeTransaction = SavingsAccountTransaction.fee(this, transactionDate, feeAmount);
                    this.transactions.add(withdrawalFeeTransaction);
                break;
            }
        }

        validateAccountBalanceDoesNotBecomeNegative(retreiveListOfTransactions(), transactionAmountMoney);

        this.summary.updateSummary(this.currency, this.savingsAccountTransactionSummaryWrapper, this.transactions);

        return transaction;
    }

    public boolean isBeforeLastPostingPeriod(final LocalDate transactionDate) {

        boolean transactionBeforeLastInterestPosting = false;

        for (SavingsAccountTransaction transaction : retreiveListOfTransactions()) {
            if (transaction.isNotReversed() && transaction.isInterestPosting() && transaction.isAfter(transactionDate)) {
                transactionBeforeLastInterestPosting = true;
                break;
            }
        }

        return transactionBeforeLastInterestPosting;
    }

    private void validateAccountBalanceDoesNotBecomeNegative(final List<SavingsAccountTransaction> transactionsSortedByDate,
            final Money transactionAmount) {

        Money runningBalance = Money.zero(this.currency);

        for (SavingsAccountTransaction transaction : transactionsSortedByDate) {
            if (transaction.isNotReversed() && transaction.isDeposit() || transaction.isInterestPosting()) {
                runningBalance = runningBalance.plus(transaction.getAmount(this.currency));
            } else if (transaction.isNotReversed() && transaction.isWithdrawal()) {
                runningBalance = runningBalance.minus(transaction.getAmount(this.currency));
            }

            if (runningBalance.isLessThanZero()) {
                //
                final BigDecimal withdrawalFee = null;
                throw new InsufficientAccountBalanceException("transactionAmount", getAccountBalance(), withdrawalFee,
                        transactionAmount.getAmount());
            }
        }
    }

    private void validateAccountBalanceDoesNotBecomeNegativeDueToUndoAction(final List<SavingsAccountTransaction> transactionsSortedByDate) {

        Money runningBalance = Money.zero(this.currency);

        for (SavingsAccountTransaction transaction : transactionsSortedByDate) {
            if (transaction.isNotReversed() && transaction.isDeposit() || transaction.isInterestPosting()) {
                runningBalance = runningBalance.plus(transaction.getAmount(this.currency));
            } else if (transaction.isNotReversed() && transaction.isWithdrawal()) {
                runningBalance = runningBalance.minus(transaction.getAmount(this.currency));
            }

            if (runningBalance.isLessThanZero()) {
                //
                final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
                final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                        .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.undoTransactionAction);

                baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("results.in.balance.going.negative");

                if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
            }
        }
    }

    public SavingsAccountTransaction addAnnualFee(final MathContext mc, final DateTimeFormatter formatter, final LocalDate transactionDate,
            final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds) {

        if (isNotActive()) {

            final String defaultUserMessage = "Transaction is not allowed. Account is not active.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.account.is.not.active",
                    defaultUserMessage, "transactionDate", transactionDate.toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (isDateInTheFuture(transactionDate)) {
            final String defaultUserMessage = "Transaction date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.in.the.future",
                    defaultUserMessage, "transactionDate", transactionDate.toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (transactionDate.isBefore(getActivationLocalDate())) {
            final Object[] defaultUserArgs = Arrays.asList(transactionDate.toString(formatter),
                    getActivationLocalDate().toString(formatter)).toArray();
            final String defaultUserMessage = "Transaction date cannot be before accounts activation date.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.before.activation.date",
                    defaultUserMessage, "transactionDate", defaultUserArgs);

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (isAccountLocked(transactionDate)) {
            final String defaultUserMessage = "Withdrawal is not allowed. No withdrawals are allowed until after "
                    + getLockedInUntilLocalDate().toString(formatter);
            final ApiParameterError error = ApiParameterError.parameterError(
                    "error.msg.savingsaccount.transaction.withdrawals.blocked.during.lockin.period", defaultUserMessage, "transactionDate",
                    transactionDate.toString(formatter), getLockedInUntilLocalDate().toString(formatter));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        existingTransactionIds.addAll(findExistingTransactionIds());
        existingReversedTransactionIds.addAll(findExistingReversedTransactionIds());

        final Money annualFee = Money.of(this.currency, this.annualFeeAmount);
        SavingsAccountTransaction annualFeeTransaction = SavingsAccountTransaction.annualFee(this, transactionDate, annualFee);
        this.transactions.add(annualFeeTransaction);

        this.summary.updateSummary(this.currency, this.savingsAccountTransactionSummaryWrapper, this.transactions);

        final LocalDate today = DateUtils.getLocalDateOfTenant();
        calculateInterestUsing(mc, today);

        updateAnnualFeeNextDueDate();

        return annualFeeTransaction;
    }

    private boolean isAutomaticWithdrawalFee() {
        return this.withdrawalFeeType != null;
    }

    private boolean isAccountLocked(final LocalDate transactionDate) {
        boolean isLocked = false;
        final boolean accountHasLockedInSetting = this.lockedInUntilDate != null;
        if (accountHasLockedInSetting) {
            isLocked = getLockedInUntilLocalDate().isAfter(transactionDate);
        }
        return isLocked;
    }

    private LocalDate getLockedInUntilLocalDate() {
        LocalDate lockedInUntilLocalDate = null;
        if (this.lockedInUntilDate != null) {
            lockedInUntilLocalDate = new LocalDate(this.lockedInUntilDate);
        }
        return lockedInUntilLocalDate;
    }

    private boolean isDateInTheFuture(final LocalDate transactionDate) {
        return transactionDate.isAfter(DateUtils.getLocalDateOfTenant());
    }

    private BigDecimal getAccountBalance() {
        return this.summary.getAccountBalance(this.currency).getAmount();
    }

    public void modifyApplication(final JsonCommand command, final Map<String, Object> actualChanges) {

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.modifyApplicationAction);

        SavingsAccountStatusType currentStatus = SavingsAccountStatusType.fromInt(this.status);
        if (!SavingsAccountStatusType.SUBMITTED_AND_PENDING_APPROVAL.hasStateOf(currentStatus)) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("not.in.submittedandpendingapproval.state");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        final String localeAsInput = command.locale();
        final String dateFormat = command.dateFormat();

        if (command.isChangeInLocalDateParameterNamed(SavingsApiConstants.submittedOnDateParamName, getSubmittedOnLocalDate())) {
            final LocalDate newValue = command.localDateValueOfParameterNamed(SavingsApiConstants.submittedOnDateParamName);
            final String newValueAsString = command.stringValueOfParameterNamed(SavingsApiConstants.submittedOnDateParamName);
            actualChanges.put(SavingsApiConstants.submittedOnDateParamName, newValueAsString);
            actualChanges.put(SavingsApiConstants.localeParamName, localeAsInput);
            actualChanges.put(SavingsApiConstants.dateFormatParamName, dateFormat);
            this.submittedOnDate = newValue.toDate();
        }

        if (command.isChangeInStringParameterNamed(SavingsApiConstants.accountNoParamName, this.accountNumber)) {
            final String newValue = command.stringValueOfParameterNamed(SavingsApiConstants.accountNoParamName);
            actualChanges.put(SavingsApiConstants.accountNoParamName, newValue);
            this.accountNumber = StringUtils.defaultIfEmpty(newValue, null);
        }

        if (command.isChangeInStringParameterNamed(SavingsApiConstants.externalIdParamName, this.externalId)) {
            final String newValue = command.stringValueOfParameterNamed(SavingsApiConstants.externalIdParamName);
            actualChanges.put(SavingsApiConstants.externalIdParamName, newValue);
            this.externalId = StringUtils.defaultIfEmpty(newValue, null);
        }

        if (command.isChangeInLongParameterNamed(SavingsApiConstants.clientIdParamName, clientId())) {
            final Long newValue = command.longValueOfParameterNamed(SavingsApiConstants.clientIdParamName);
            actualChanges.put(SavingsApiConstants.clientIdParamName, newValue);
        }

        if (command.isChangeInLongParameterNamed(SavingsApiConstants.groupIdParamName, groupId())) {
            final Long newValue = command.longValueOfParameterNamed(SavingsApiConstants.groupIdParamName);
            actualChanges.put(SavingsApiConstants.groupIdParamName, newValue);
        }

        if (command.isChangeInLongParameterNamed(SavingsApiConstants.productIdParamName, this.product.getId())) {
            final Long newValue = command.longValueOfParameterNamed(SavingsApiConstants.productIdParamName);
            actualChanges.put(SavingsApiConstants.productIdParamName, newValue);
        }

        if (command.isChangeInLongParameterNamed(SavingsApiConstants.fieldOfficerIdParamName, fieldOfficerId())) {
            final Long newValue = command.longValueOfParameterNamed(SavingsApiConstants.fieldOfficerIdParamName);
            actualChanges.put(SavingsApiConstants.fieldOfficerIdParamName, newValue);
        }

        if (command.isChangeInBigDecimalParameterNamed(SavingsApiConstants.nominalAnnualInterestRateParamName,
                this.nominalAnnualInterestRate)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(SavingsApiConstants.nominalAnnualInterestRateParamName);
            actualChanges.put(SavingsApiConstants.nominalAnnualInterestRateParamName, newValue);
            actualChanges.put("locale", localeAsInput);
            this.nominalAnnualInterestRate = newValue;
        }

        if (command.isChangeInIntegerParameterNamed(SavingsApiConstants.interestCompoundingPeriodTypeParamName,
                this.interestCompoundingPeriodType)) {
            final Integer newValue = command.integerValueOfParameterNamed(SavingsApiConstants.interestCompoundingPeriodTypeParamName);
            this.interestCompoundingPeriodType = newValue != null ? SavingsCompoundingInterestPeriodType.fromInt(newValue).getValue()
                    : newValue;
            actualChanges.put(SavingsApiConstants.interestCompoundingPeriodTypeParamName, this.interestCompoundingPeriodType);
        }

        if (command.isChangeInIntegerParameterNamed(SavingsApiConstants.interestPostingPeriodTypeParamName, this.interestPostingPeriodType)) {
            final Integer newValue = command.integerValueOfParameterNamed(SavingsApiConstants.interestPostingPeriodTypeParamName);
            this.interestPostingPeriodType = newValue != null ? SavingsPostingInterestPeriodType.fromInt(newValue).getValue() : newValue;
            actualChanges.put(SavingsApiConstants.interestPostingPeriodTypeParamName, this.interestPostingPeriodType);
        }

        if (command.isChangeInIntegerParameterNamed(SavingsApiConstants.interestCalculationTypeParamName, this.interestCalculationType)) {
            final Integer newValue = command.integerValueOfParameterNamed(SavingsApiConstants.interestCalculationTypeParamName);
            this.interestCalculationType = newValue != null ? SavingsInterestCalculationType.fromInt(newValue).getValue() : newValue;
            actualChanges.put(SavingsApiConstants.interestCalculationTypeParamName, this.interestCalculationType);
        }

        if (command.isChangeInIntegerParameterNamed(SavingsApiConstants.interestCalculationDaysInYearTypeParamName,
                this.interestCalculationDaysInYearType)) {
            final Integer newValue = command.integerValueOfParameterNamed(SavingsApiConstants.interestCalculationDaysInYearTypeParamName);
            this.interestCalculationDaysInYearType = newValue != null ? SavingsInterestCalculationDaysInYearType.fromInt(newValue)
                    .getValue() : newValue;
            actualChanges.put(SavingsApiConstants.interestCalculationDaysInYearTypeParamName, this.interestCalculationDaysInYearType);
        }

        if (command.isChangeInBigDecimalParameterNamedDefaultingZeroToNull(SavingsApiConstants.minRequiredOpeningBalanceParamName,
                this.minRequiredOpeningBalance)) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamedDefaultToNullIfZero(SavingsApiConstants.minRequiredOpeningBalanceParamName);
            actualChanges.put(SavingsApiConstants.minRequiredOpeningBalanceParamName, newValue);
            actualChanges.put("locale", localeAsInput);
            this.minRequiredOpeningBalance = Money.of(this.currency, newValue).getAmount();
        }

        if (command.isChangeInIntegerParameterNamedDefaultingZeroToNull(SavingsApiConstants.lockinPeriodFrequencyParamName,
                this.lockinPeriodFrequency)) {
            final Integer newValue = command
                    .integerValueOfParameterNamedDefaultToNullIfZero(SavingsApiConstants.lockinPeriodFrequencyParamName);
            actualChanges.put(SavingsApiConstants.lockinPeriodFrequencyParamName, newValue);
            actualChanges.put("locale", localeAsInput);
            this.lockinPeriodFrequency = newValue;
        }

        if (command.isChangeInIntegerParameterNamed(SavingsApiConstants.lockinPeriodFrequencyTypeParamName, this.lockinPeriodFrequencyType)) {
            final Integer newValue = command.integerValueOfParameterNamed(SavingsApiConstants.lockinPeriodFrequencyTypeParamName);
            actualChanges.put(SavingsApiConstants.lockinPeriodFrequencyTypeParamName, newValue);
            this.lockinPeriodFrequencyType = newValue != null ? SavingsPeriodFrequencyType.fromInt(newValue).getValue() : newValue;
        }

        // set period type to null if frequency is null
        if (this.lockinPeriodFrequency == null) {
            this.lockinPeriodFrequencyType = null;
        }

        if (command.isChangeInBigDecimalParameterNamedDefaultingZeroToNull(withdrawalFeeAmountParamName, this.withdrawalFeeAmount)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamedDefaultToNullIfZero(withdrawalFeeAmountParamName);
            actualChanges.put(withdrawalFeeAmountParamName, newValue);
            actualChanges.put(localeParamName, localeAsInput);
            this.withdrawalFeeAmount = newValue;
        }

        if (command.isChangeInIntegerParameterNamedDefaultingZeroToNull(withdrawalFeeTypeParamName, this.withdrawalFeeType)) {
            final Integer newValue = command.integerValueOfParameterNamedDefaultToNullIfZero(withdrawalFeeTypeParamName);
            actualChanges.put(withdrawalFeeTypeParamName, newValue);
            this.withdrawalFeeType = newValue != null ? SavingsWithdrawalFeesType.fromInt(newValue).getValue() : newValue;
        }

        // set period type to null if frequency is null
        if (this.withdrawalFeeAmount == null) {
            this.withdrawalFeeType = null;
        }

        if (command.isChangeInBigDecimalParameterNamedDefaultingZeroToNull(annualFeeAmountParamName, this.annualFeeAmount)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamedDefaultToNullIfZero(annualFeeAmountParamName);
            actualChanges.put(annualFeeAmountParamName, newValue);
            actualChanges.put(localeParamName, localeAsInput);
            this.annualFeeAmount = newValue;
        }

        if (command.isChangeInIntegerParameterNamedDefaultingZeroToNull(annualFeeOnMonthDayParamName, this.annualFeeOnDay)) {
            final MonthDay monthDay = command.extractMonthDayNamed(annualFeeOnMonthDayParamName);
            final String actualValueEntered = command.stringValueOfParameterNamed(annualFeeOnMonthDayParamName);
            final Integer newValue = monthDay.getDayOfMonth();
            actualChanges.put(annualFeeOnMonthDayParamName, actualValueEntered);
            actualChanges.put(localeParamName, localeAsInput);
            this.annualFeeOnDay = newValue;
        }

        if (command.isChangeInIntegerParameterNamedDefaultingZeroToNull(annualFeeOnMonthDayParamName, this.annualFeeOnMonth)) {
            final MonthDay monthDay = command.extractMonthDayNamed(annualFeeOnMonthDayParamName);
            final String actualValueEntered = command.stringValueOfParameterNamed(annualFeeOnMonthDayParamName);
            final Integer newValue = monthDay.getMonthOfYear();
            actualChanges.put(annualFeeOnMonthDayParamName, actualValueEntered);
            actualChanges.put(localeParamName, localeAsInput);
            this.annualFeeOnMonth = newValue;
        }

        // set period type to null if frequency is null
        if (this.annualFeeAmount == null) {
            this.annualFeeOnDay = null;
            this.annualFeeOnMonth = null;
        }

        if (this.annualFeeOnDay != null && this.annualFeeOnMonth != null) {
            updateAnnualFeeNextDueDate();
        }

        validateLockinDetails();
        validateWithdrawalFeeDetails();
        validateAnnualFeeDetails();
    }

    private void validateAnnualFeeDetails() {

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME);

        if (this.annualFeeAmount == null) {

            if (this.annualFeeOnMonth != null || this.annualFeeOnDay != null) {
                baseDataValidator.reset().parameter(annualFeeAmountParamName).value(this.annualFeeAmount).notNull();
            }
        } else {

            if (this.annualFeeOnMonth == null || this.annualFeeOnDay == null) {
                baseDataValidator.reset().parameter(annualFeeOnMonthDayParamName).value(this.annualFeeOnMonth).notNull();
            }

            baseDataValidator.reset().parameter(annualFeeAmountParamName).value(this.annualFeeAmount).zeroOrPositiveAmount();
        }

        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
    }

    private void validateWithdrawalFeeDetails() {

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME);

        if (this.withdrawalFeeAmount == null) {
            baseDataValidator.reset().parameter(withdrawalFeeTypeParamName).value(this.withdrawalFeeType).ignoreIfNull()
                    .isOneOfTheseValues(1, 2);

            if (this.withdrawalFeeType != null) {
                baseDataValidator.reset().parameter(withdrawalFeeAmountParamName).value(this.withdrawalFeeAmount).notNull();
            }
        } else {
            baseDataValidator.reset().parameter(withdrawalFeeAmountParamName).value(this.withdrawalFeeAmount).zeroOrPositiveAmount();
            baseDataValidator.reset().parameter(withdrawalFeeTypeParamName).value(this.withdrawalFeeType).notNull()
                    .isOneOfTheseValues(1, 2);
        }

        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
    }

    private void validateLockinDetails() {

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME);

        if (this.lockinPeriodFrequency == null) {
            baseDataValidator.reset().parameter(lockinPeriodFrequencyTypeParamName).value(lockinPeriodFrequencyType).ignoreIfNull()
                    .inMinMaxRange(0, 3);

            if (this.lockinPeriodFrequencyType != null) {
                baseDataValidator.reset().parameter(lockinPeriodFrequencyParamName).value(lockinPeriodFrequency).notNull()
                        .integerZeroOrGreater();
            }
        } else {
            baseDataValidator.reset().parameter(lockinPeriodFrequencyParamName).value(lockinPeriodFrequencyType).integerZeroOrGreater();
            baseDataValidator.reset().parameter(lockinPeriodFrequencyTypeParamName).value(lockinPeriodFrequencyType).notNull()
                    .inMinMaxRange(0, 3);
        }

        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
    }

    public Map<String, Object> deriveAccountingBridgeData(final CurrencyData currencyData, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds) {

        final Map<String, Object> accountingBridgeData = new LinkedHashMap<String, Object>();
        accountingBridgeData.put("savingsId", this.getId());
        accountingBridgeData.put("savingsProductId", this.productId());
        accountingBridgeData.put("officeId", this.officeId());
        accountingBridgeData.put("cashBasedAccountingEnabled", this.isCashBasedAccountingEnabledOnSavingsProduct());
        accountingBridgeData.put("accrualBasedAccountingEnabled", this.isAccrualBasedAccountingEnabledOnSavingsProduct());

        final List<Map<String, Object>> newLoanTransactions = new ArrayList<Map<String, Object>>();
        for (SavingsAccountTransaction transaction : this.transactions) {
            if (transaction.isReversed() && !existingReversedTransactionIds.contains(transaction.getId())) {
                newLoanTransactions.add(transaction.toMapData(currencyData));
            } else if (!existingTransactionIds.contains(transaction.getId())) {
                newLoanTransactions.add(transaction.toMapData(currencyData));
            }
        }

        accountingBridgeData.put("newSavingsTransactions", newLoanTransactions);
        return accountingBridgeData;
    }

    private Collection<Long> findExistingTransactionIds() {

        Collection<Long> ids = new ArrayList<Long>();

        for (SavingsAccountTransaction transaction : this.transactions) {
            ids.add(transaction.getId());
        }

        return ids;
    }

    private Collection<Long> findExistingReversedTransactionIds() {

        final Collection<Long> ids = new ArrayList<Long>();

        for (SavingsAccountTransaction transaction : this.transactions) {
            if (transaction.isReversed()) {
                ids.add(transaction.getId());
            }
        }

        return ids;
    }

    public void update(final Client client) {
        this.client = client;
    }

    public void update(final Group group) {
        this.group = group;
    }

    public void update(final SavingsProduct product) {
        this.product = product;
    }

    public void update(final Staff fieldOfficer) {
        this.fieldOfficer = fieldOfficer;
    }

    public void updateAccountNo(final String newAccountNo) {
        this.accountNumber = newAccountNo;
        this.accountNumberRequiresAutoGeneration = false;
    }

    public boolean isAccountNumberRequiresAutoGeneration() {
        return this.accountNumberRequiresAutoGeneration;
    }

    public Long productId() {
        return this.product.getId();
    }

    private Boolean isCashBasedAccountingEnabledOnSavingsProduct() {
        return this.product.isCashBasedAccountingEnabled();
    }

    private Boolean isAccrualBasedAccountingEnabledOnSavingsProduct() {
        return this.product.isAccrualBasedAccountingEnabled();
    }

    public Long officeId() {
        Long officeId = null;
        if (this.client != null) {
            officeId = this.client.officeId();
        } else {
            officeId = this.group.officeId();
        }
        return officeId;
    }

    public Long clientId() {
        Long id = null;
        if (this.client != null) {
            id = this.client.getId();
        }
        return id;
    }

    public Long groupId() {
        Long id = null;
        if (this.group != null) {
            id = this.group.getId();
        }
        return id;
    }

    public Long fieldOfficerId() {
        Long id = null;
        if (this.fieldOfficer != null) {
            id = this.fieldOfficer.getId();
        }
        return id;
    }

    public MonetaryCurrency getCurrency() {
        return this.currency;
    }

    public void validateNewApplicationState(final LocalDate todayDateOfTenant) {

        validateLockinDetails();
        validateWithdrawalFeeDetails();
        validateAnnualFeeDetails();

        final LocalDate submittedOn = getSubmittedOnLocalDate();

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.summitalAction);

        if (submittedOn.isAfter(todayDateOfTenant)) {
            baseDataValidator.reset().parameter(SavingsApiConstants.submittedOnDateParamName).value(submittedOn)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.a.future.date");
        }

        if (client != null && client.isActivatedAfter(submittedOn)) {
            baseDataValidator.reset().parameter(SavingsApiConstants.submittedOnDateParamName).value(client.getActivationLocalDate())
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.before.client.activation.date");
        } else if (group != null && group.isActivatedAfter(submittedOn)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.submittedOnDateParamName).value(group.getActivationLocalDate())
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.before.client.activation.date");
        }

        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
    }

    private LocalDate getSubmittedOnLocalDate() {
        LocalDate submittedOn = null;
        if (this.submittedOnDate != null) {
            submittedOn = new LocalDate(this.submittedOnDate);
        }
        return submittedOn;
    }

    private LocalDate getApprovedOnLocalDate() {
        LocalDate approvedOnLocalDate = null;
        if (this.approvedOnDate != null) {
            approvedOnLocalDate = new LocalDate(this.approvedOnDate);
        }
        return approvedOnLocalDate;
    }

    public Client getClient() {
        return this.client;
    }

    public Map<String, Object> approveApplication(final AppUser currentUser, final JsonCommand command, final LocalDate tenantsTodayDate) {

        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>();

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.approvalAction);

        SavingsAccountStatusType currentStatus = SavingsAccountStatusType.fromInt(this.status);
        if (!SavingsAccountStatusType.SUBMITTED_AND_PENDING_APPROVAL.hasStateOf(currentStatus)) {
            baseDataValidator.reset().parameter(SavingsApiConstants.approvedOnDateParamName)
                    .failWithCodeNoParameterAddedToErrorCode("not.in.submittedandpendingapproval.state");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        this.status = SavingsAccountStatusType.APPROVED.getValue();
        actualChanges.put(SavingsApiConstants.statusParamName, SavingsEnumerations.status(this.status));

        // only do below if status has changed in the 'approval' case
        LocalDate approvedOn = command.localDateValueOfParameterNamed(SavingsApiConstants.approvedOnDateParamName);
        String approvedOnDateChange = command.stringValueOfParameterNamed(SavingsApiConstants.approvedOnDateParamName);

        this.approvedOnDate = approvedOn.toDate();
        this.approvedBy = currentUser;
        actualChanges.put(SavingsApiConstants.localeParamName, command.locale());
        actualChanges.put(SavingsApiConstants.dateFormatParamName, command.dateFormat());
        actualChanges.put(SavingsApiConstants.approvedOnDateParamName, approvedOnDateChange);

        final LocalDate submittalDate = getSubmittedOnLocalDate();
        if (approvedOn.isBefore(submittalDate)) {

            final DateTimeFormatter formatter = DateTimeFormat.forPattern(command.dateFormat()).withLocale(command.extractLocale());
            final String submittalDateAsString = formatter.print(submittalDate);

            baseDataValidator.reset().parameter(SavingsApiConstants.approvedOnDateParamName).value(submittalDateAsString)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.before.submittal.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        if (approvedOn.isAfter(tenantsTodayDate)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.approvedOnDateParamName)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.a.future.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        // FIXME - kw - support field officer history for savings accounts
        // if (this.fieldOfficer != null) {
        // final LoanOfficerAssignmentHistory loanOfficerAssignmentHistory =
        // LoanOfficerAssignmentHistory.createNew(this,
        // this.fieldOfficer, approvedOn);
        // this.loanOfficerHistory.add(loanOfficerAssignmentHistory);
        // }

        return actualChanges;
    }

    public Map<String, Object> undoApplicationApproval() {
        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>();

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.undoApprovalAction);

        SavingsAccountStatusType currentStatus = SavingsAccountStatusType.fromInt(this.status);
        if (!SavingsAccountStatusType.APPROVED.hasStateOf(currentStatus)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.approvedOnDateParamName)
                    .failWithCodeNoParameterAddedToErrorCode("not.in.approved.state");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        this.status = SavingsAccountStatusType.SUBMITTED_AND_PENDING_APPROVAL.getValue();
        actualChanges.put(SavingsApiConstants.statusParamName, SavingsEnumerations.status(this.status));

        this.approvedOnDate = null;
        this.approvedBy = null;
        this.rejectedOnDate = null;
        this.rejectedBy = null;
        this.withdrawnOnDate = null;
        this.withdrawnBy = null;
        this.closedOnDate = null;
        this.closedBy = null;
        actualChanges.put(SavingsApiConstants.approvedOnDateParamName, "");

        // FIXME - kw - support field officer history for savings accounts
        // this.loanOfficerHistory.clear();

        return actualChanges;
    }

    public void undoTransaction(final Long transactionId, final List<Long> reversedTransactionIds, final MathContext mc,
            final LocalDate upToInterestCalculationDate) {

        SavingsAccountTransaction transactionToUndo = null;
        for (SavingsAccountTransaction transaction : this.transactions) {
            if (transaction.isIdentifiedBy(transactionId)) {
                transactionToUndo = transaction;
            }
        }

        if (transactionToUndo == null) {
            // throw non found exception

        } else {
            transactionToUndo.reverse();
            reversedTransactionIds.add(transactionId);

            calculateInterestUsing(mc, upToInterestCalculationDate);

            validateAccountBalanceDoesNotBecomeNegativeDueToUndoAction(retreiveListOfTransactions());
        }
    }

    public Map<String, Object> rejectApplication(final AppUser currentUser, final JsonCommand command, final LocalDate tenantsTodayDate) {

        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>();

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.rejectAction);

        SavingsAccountStatusType currentStatus = SavingsAccountStatusType.fromInt(this.status);
        if (!SavingsAccountStatusType.SUBMITTED_AND_PENDING_APPROVAL.hasStateOf(currentStatus)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.rejectedOnDateParamName)
                    .failWithCodeNoParameterAddedToErrorCode("not.in.submittedandpendingapproval.state");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        this.status = SavingsAccountStatusType.REJECTED.getValue();
        actualChanges.put(SavingsApiConstants.statusParamName, SavingsEnumerations.status(this.status));

        LocalDate rejectedOn = command.localDateValueOfParameterNamed(SavingsApiConstants.rejectedOnDateParamName);
        final String rejectedOnAsString = command.stringValueOfParameterNamed(SavingsApiConstants.rejectedOnDateParamName);

        this.rejectedOnDate = rejectedOn.toDate();
        this.rejectedBy = currentUser;
        this.withdrawnOnDate = null;
        this.withdrawnBy = null;
        this.closedOnDate = rejectedOn.toDate();
        this.closedBy = currentUser;

        actualChanges.put(SavingsApiConstants.localeParamName, command.locale());
        actualChanges.put(SavingsApiConstants.dateFormatParamName, command.dateFormat());
        actualChanges.put(SavingsApiConstants.rejectedOnDateParamName, rejectedOnAsString);
        actualChanges.put(SavingsApiConstants.closedOnDateParamName, rejectedOnAsString);

        final LocalDate submittalDate = getSubmittedOnLocalDate();
        if (rejectedOn.isBefore(submittalDate)) {

            final DateTimeFormatter formatter = DateTimeFormat.forPattern(command.dateFormat()).withLocale(command.extractLocale());
            final String submittalDateAsString = formatter.print(submittalDate);

            baseDataValidator.reset().parameter(SavingsApiConstants.rejectedOnDateParamName).value(submittalDateAsString)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.before.submittal.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        if (rejectedOn.isAfter(tenantsTodayDate)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.rejectedOnDateParamName).value(rejectedOn)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.a.future.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        return actualChanges;
    }

    public Map<String, Object> applicantWithdrawsFromApplication(final AppUser currentUser, final JsonCommand command,
            final LocalDate tenantsTodayDate) {
        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>();

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.withdrawnByApplicantAction);

        SavingsAccountStatusType currentStatus = SavingsAccountStatusType.fromInt(this.status);
        if (!SavingsAccountStatusType.SUBMITTED_AND_PENDING_APPROVAL.hasStateOf(currentStatus)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.withdrawnOnDateParamName)
                    .failWithCodeNoParameterAddedToErrorCode("not.in.submittedandpendingapproval.state");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        this.status = SavingsAccountStatusType.WITHDRAWN_BY_APPLICANT.getValue();
        actualChanges.put(SavingsApiConstants.statusParamName, SavingsEnumerations.status(this.status));

        final LocalDate withdrawnOn = command.localDateValueOfParameterNamed(SavingsApiConstants.withdrawnOnDateParamName);
        final String withdrawnOnAsString = command.stringValueOfParameterNamed(SavingsApiConstants.withdrawnOnDateParamName);

        this.rejectedOnDate = null;
        this.rejectedBy = null;
        this.withdrawnOnDate = withdrawnOn.toDate();
        this.withdrawnBy = currentUser;
        this.closedOnDate = withdrawnOn.toDate();
        this.closedBy = currentUser;

        actualChanges.put(SavingsApiConstants.localeParamName, command.locale());
        actualChanges.put(SavingsApiConstants.dateFormatParamName, command.dateFormat());
        actualChanges.put(SavingsApiConstants.withdrawnOnDateParamName, withdrawnOnAsString);
        actualChanges.put(SavingsApiConstants.closedOnDateParamName, withdrawnOnAsString);

        final LocalDate submittalDate = getSubmittedOnLocalDate();
        if (withdrawnOn.isBefore(submittalDate)) {

            final DateTimeFormatter formatter = DateTimeFormat.forPattern(command.dateFormat()).withLocale(command.extractLocale());
            final String submittalDateAsString = formatter.print(submittalDate);

            baseDataValidator.reset().parameter(SavingsApiConstants.withdrawnOnDateParamName).value(submittalDateAsString)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.before.submittal.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        if (withdrawnOn.isAfter(tenantsTodayDate)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.withdrawnOnDateParamName).value(withdrawnOn)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.a.future.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        return actualChanges;
    }

    public Map<String, Object> activate(final AppUser currentUser, final JsonCommand command, final LocalDate tenantsTodayDate,
            final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds) {

        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>();

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME + SavingsApiConstants.activateAction);

        SavingsAccountStatusType currentStatus = SavingsAccountStatusType.fromInt(this.status);
        if (!SavingsAccountStatusType.APPROVED.hasStateOf(currentStatus)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.activatedOnDateParamName)
                    .failWithCodeNoParameterAddedToErrorCode("not.in.approved.state");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormat.forPattern(command.dateFormat()).withLocale(locale);
        final LocalDate activationDate = command.localDateValueOfParameterNamed(SavingsApiConstants.activatedOnDateParamName);

        this.status = SavingsAccountStatusType.ACTIVE.getValue();
        actualChanges.put(SavingsApiConstants.statusParamName, SavingsEnumerations.status(this.status));
        actualChanges.put(SavingsApiConstants.localeParamName, command.locale());
        actualChanges.put(SavingsApiConstants.dateFormatParamName, command.dateFormat());
        actualChanges.put(SavingsApiConstants.activatedOnDateParamName, activationDate.toString(fmt));

        this.rejectedOnDate = null;
        this.rejectedBy = null;
        this.withdrawnOnDate = null;
        this.withdrawnBy = null;
        this.closedOnDate = null;
        this.closedBy = null;
        this.activatedOnDate = activationDate.toDate();
        this.activatedBy = currentUser;
        this.lockedInUntilDate = calculateDateAccountIsLockedUntil(getActivationLocalDate());

        if (this.client.isActivatedAfter(activationDate)) {

            final DateTimeFormatter formatter = DateTimeFormat.forPattern(command.dateFormat()).withLocale(command.extractLocale());
            final String dateAsString = formatter.print(client.getActivationLocalDate());

            baseDataValidator.reset().parameter(SavingsApiConstants.activatedOnDateParamName).value(dateAsString)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.before.client.activation.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        final LocalDate approvalDate = getApprovedOnLocalDate();
        if (activationDate.isBefore(approvalDate)) {

            final DateTimeFormatter formatter = DateTimeFormat.forPattern(command.dateFormat()).withLocale(command.extractLocale());
            final String dateAsString = formatter.print(approvalDate);

            baseDataValidator.reset().parameter(SavingsApiConstants.activatedOnDateParamName).value(dateAsString)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.before.approval.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        if (activationDate.isAfter(tenantsTodayDate)) {

            baseDataValidator.reset().parameter(SavingsApiConstants.activatedOnDateParamName).value(activationDate)
                    .failWithCodeNoParameterAddedToErrorCode("cannot.be.a.future.date");

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        }

        // auto enter deposit for minimum required opening balance when
        // activating account.
        final Money minRequiredOpeningBalance = Money.of(this.currency, this.minRequiredOpeningBalance);
        if (minRequiredOpeningBalance.isGreaterThanZero()) {
            deposit(fmt, activationDate, minRequiredOpeningBalance.getAmount(), existingTransactionIds, existingReversedTransactionIds,
                    null);
        }

        return actualChanges;
    }

    private Date calculateDateAccountIsLockedUntil(final LocalDate activationLocalDate) {

        Date lockedInUntilLocalDate = null;
        final PeriodFrequencyType lockinPeriodFrequencyType = PeriodFrequencyType.fromInt(this.lockinPeriodFrequencyType);
        switch (lockinPeriodFrequencyType) {
            case INVALID:
            break;
            case DAYS:
                lockedInUntilLocalDate = activationLocalDate.plusDays(this.lockinPeriodFrequency).toDate();
            break;
            case WEEKS:
                lockedInUntilLocalDate = activationLocalDate.plusWeeks(this.lockinPeriodFrequency).toDate();
            break;
            case MONTHS:
                lockedInUntilLocalDate = activationLocalDate.plusMonths(this.lockinPeriodFrequency).toDate();
            break;
            case YEARS:
                lockedInUntilLocalDate = activationLocalDate.plusYears(this.lockinPeriodFrequency).toDate();
            break;
        }

        return lockedInUntilLocalDate;
    }
}