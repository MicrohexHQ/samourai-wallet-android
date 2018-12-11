package com.samourai.wallet.spend;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.dm.zbar.android.scanner.ZBarConstants;
import com.dm.zbar.android.scanner.ZBarScannerActivity;
import com.samourai.wallet.BatchSendActivity;
import com.samourai.wallet.R;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.TxAnimUIActivity;
import com.samourai.wallet.UTXOActivity;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.bip47.BIP47Activity;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.fragments.PaynymSelectModalFragment;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.ricochet.RicochetActivity;
import com.samourai.wallet.ricochet.RicochetMeta;
import com.samourai.wallet.segwit.BIP49Util;
import com.samourai.wallet.segwit.BIP84Util;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.send.FeeUtil;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.SendFactory;
import com.samourai.wallet.send.SendParams;
import com.samourai.wallet.send.SuggestedFee;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.send.UTXOFactory;
import com.samourai.wallet.spend.widgets.EntropyBar;
import com.samourai.wallet.spend.widgets.SendTransactionDetailsView;
import com.samourai.wallet.util.AddressFactory;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.wallet.util.MonetaryUtil;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.SendAddressUtil;
import com.yanzhenjie.zbar.Symbol;

import org.apache.commons.lang3.tuple.Triple;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.script.Script;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Vector;


public class SendActivity extends AppCompatActivity {


    private final static int SCAN_QR = 2012;
    private final static int RICOCHET = 2013;
    private static final String TAG = "SendActivity";


    private SendTransactionDetailsView sendTransactionDetailsView;
    private ViewSwitcher amountViewSwitcher;
    private EditText toAddressEditText, btcEditText, satEditText;
    private TextView tvMaxAmount, tvReviewSpendAmount, tvTotalFee, tvToAddress, tvEstimatedBlockWait, tvSelectedFeeRate, tvSelectedFeeRateLayman;
    private Button btnReview, btnSend;
    private ImageView selectPaynymBtn;
    private Switch ricochetHopsSwitch, stoneWallSwitch;
    private SeekBar feeSeekBar;
    private EntropyBar entropyBar;
//    BottomSheetBehavior sheetBehavior;

    private long balance = 0L;
    private String strDestinationBTCAddress = null;

    private final static int FEE_LOW = 0;
    private final static int FEE_NORMAL = 1;
    private final static int FEE_PRIORITY = 2;
    private final static int FEE_CUSTOM = 3;
    private int FEE_TYPE = FEE_LOW;

    public final static int SPEND_SIMPLE = 0;
    public final static int SPEND_BOLTZMANN = 1;
    public final static int SPEND_RICOCHET = 2;
    private int SPEND_TYPE = SPEND_BOLTZMANN;

    private int selectedAccount = 0;

    private String strPCode = null;
    private long feeLow, feeMed, feeHigh;
    private String strPrivacyWarning;
    private ArrayList<UTXO> selectedUTXO;
    private long _change;
    private HashMap<String, BigInteger> receivers;
    private int changeType;
    private String address;
    private String message;
    private long amount;
    private int change_index;
    private String ricochetMessage;
    private JSONObject ricochetJsonObj = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_new_ui);
        setSupportActionBar(findViewById(R.id.toolbar_send));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setTitle("");
        //CustomView for showing and hiding body of th UI
        sendTransactionDetailsView = findViewById(R.id.sendTransactionDetailsView);

        //ViewSwitcher Element for toolbar section of the UI.
        //we can switch between Form and review screen with this element
        amountViewSwitcher = findViewById(R.id.toolbar_view_switcher);

        //Input elements from toolbar section of the UI
        toAddressEditText = findViewById(R.id.edt_send_to);
        btcEditText = findViewById(R.id.amountBTC);
        satEditText = findViewById(R.id.amountSat);
        tvToAddress = findViewById(R.id.to_address_review);
        selectPaynymBtn = findViewById(R.id.paynym_select_btn);
        tvReviewSpendAmount = findViewById(R.id.send_review_amount);
        tvMaxAmount = findViewById(R.id.totalBTC);


        //view elements from review segment and transaction segment can be access through respective
        //methods which returns root viewGroup
        entropyBar = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.entropyBar);
        btnReview = sendTransactionDetailsView.getTransactionView().findViewById(R.id.review_button);
        ricochetHopsSwitch = sendTransactionDetailsView.getTransactionView().findViewById(R.id.ricochet_hops_switch);
        tvSelectedFeeRate = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.selected_fee_rate);
        tvSelectedFeeRateLayman = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.selected_fee_rate_in_layman);
        tvTotalFee = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.total_fee);
        btnSend = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.send_btn);
        feeSeekBar = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.fee_seekbar);
        tvEstimatedBlockWait = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.est_block_time);
        feeSeekBar = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.fee_seekbar);
        stoneWallSwitch = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.stone_wall_radio_btn);

        btcEditText.addTextChangedListener(BTCWatcher);
        satEditText.addTextChangedListener(satWatcher);

        btnReview.setOnClickListener(v -> review());
        btnSend.setOnClickListener(v -> initiateSpend());


        if (SamouraiWallet.getInstance().getShowTotalBalance()) {
            if (SamouraiWallet.getInstance().getCurrentSelectedAccount() == 2) {
                selectedAccount = 1;
            } else {
                selectedAccount = 0;
            }
        } else {
            selectedAccount = 0;
        }


        View.OnClickListener clipboardCopy = view -> {
            ClipboardManager cm = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = android.content.ClipData
                    .newPlainText("Miner fee", tvTotalFee.getText());
            if (cm != null) {
                cm.setPrimaryClip(clipData);
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        };

        tvTotalFee.setOnClickListener(clipboardCopy);
        tvSelectedFeeRate.setOnClickListener(clipboardCopy);

        SPEND_TYPE = PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_BOLTZMANN, true) ? SPEND_BOLTZMANN : SPEND_SIMPLE;
        if (SPEND_TYPE > SPEND_BOLTZMANN) {
            SPEND_TYPE = SPEND_BOLTZMANN;
            PrefsUtil.getInstance(this).setValue(PrefsUtil.SPEND_TYPE, SPEND_BOLTZMANN);
        }

        setUpRicochet();

        setUpFee();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
//            bViaMenu = extras.getBoolean("via_menu", false);
            String strUri = extras.getString("uri");
            strPCode = extras.getString("pcode");
            if (strUri != null && strUri.length() > 0) {
                processScan(strUri);
            }
            if (strPCode != null && strPCode.length() > 0) {
                processPCode(strPCode, null);
            }
        }

        setUpBoltzman();

        validateSpend();

        setBalance();

        setUpPaynym();
    }

    private void setUpBoltzman() {

        boolean useBoltzman = PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_BOLTZMANN, true);
        stoneWallSwitch.setChecked(useBoltzman);
        stoneWallSwitch.setOnCheckedChangeListener((compoundButton, checked) -> {
            PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_BOLTZMANN, checked);
        });
    }
//

    private void setUpPaynym() {
        selectPaynymBtn.setOnClickListener((view) -> {
            PaynymSelectModalFragment paynymSelectModalFragment =
                    PaynymSelectModalFragment.newInstance(code -> processPCode(code, null));
            paynymSelectModalFragment.show(getSupportFragmentManager(), "paynym_select");
        });
    }

    private void setUpFee() {


        FEE_TYPE = PrefsUtil.getInstance(this).getValue(PrefsUtil.CURRENT_FEE_TYPE, FEE_NORMAL);


        feeLow = FeeUtil.getInstance().getLowFee().getDefaultPerKB().longValue() / 1000L;
        feeMed = FeeUtil.getInstance().getNormalFee().getDefaultPerKB().longValue() / 1000L;
        feeHigh = FeeUtil.getInstance().getHighFee().getDefaultPerKB().longValue() / 1000L;

//        feeSeekBar.set((int) feeLow);
        feeSeekBar.setMax((int) ((feeHigh / 2) + feeHigh));
        if (feeLow == feeMed && feeMed == feeHigh) {
            feeLow = (long) ((double) feeMed * 0.85);
            feeHigh = (long) ((double) feeMed * 1.15);
            SuggestedFee lo_sf = new SuggestedFee();
            lo_sf.setDefaultPerKB(BigInteger.valueOf(feeLow * 1000L));
            FeeUtil.getInstance().setLowFee(lo_sf);
            SuggestedFee hi_sf = new SuggestedFee();
            hi_sf.setDefaultPerKB(BigInteger.valueOf(feeHigh * 1000L));
            FeeUtil.getInstance().setHighFee(hi_sf);
        } else if (feeLow == feeMed || feeMed == feeMed) {
            feeMed = (feeLow + feeHigh) / 2L;
            SuggestedFee mi_sf = new SuggestedFee();
            mi_sf.setDefaultPerKB(BigInteger.valueOf(feeHigh * 1000L));
            FeeUtil.getInstance().setNormalFee(mi_sf);
        } else {
            ;
        }

        if (feeLow < 1L) {
            feeLow = 1L;
            SuggestedFee lo_sf = new SuggestedFee();
            lo_sf.setDefaultPerKB(BigInteger.valueOf(feeLow * 1000L));
            FeeUtil.getInstance().setLowFee(lo_sf);
        }
        if (feeMed < 1L) {
            feeMed = 1L;
            SuggestedFee mi_sf = new SuggestedFee();
            mi_sf.setDefaultPerKB(BigInteger.valueOf(feeMed * 1000L));
            FeeUtil.getInstance().setNormalFee(mi_sf);
        }
        if (feeHigh < 1L) {
            feeHigh = 1L;
            SuggestedFee hi_sf = new SuggestedFee();
            hi_sf.setDefaultPerKB(BigInteger.valueOf(feeHigh * 1000L));
            FeeUtil.getInstance().setHighFee(hi_sf);
        }
//        tvEstimatedBlockWait.setText("6 blocks");
        tvSelectedFeeRateLayman.setText("Normal");

        FeeUtil.getInstance().sanitizeFee();

        tvSelectedFeeRate.setText((String.valueOf((int) feeMed).concat(" sats/b")));

        // android slider starts at 0
        feeSeekBar.setProgress((int) feeMed - 1);

        feeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                // here we get progress value at 0 , so we need to add 1
                double value = i + 1;
                tvSelectedFeeRate.setText(String.valueOf((int) value).concat(" sats/b"));
                if (value == 0.0) {
                    value = 1.0;
                }
                double pct = 0.0;
                int nbBlocks = 6;
                if (value <= (double) feeLow) {
                    pct = ((double) feeLow / value);
                    nbBlocks = ((Double) Math.ceil(pct * 24.0)).intValue();
                } else if (value >= (double) feeHigh) {
                    pct = ((double) feeHigh / value);
                    nbBlocks = ((Double) Math.ceil(pct * 2.0)).intValue();
                    if (nbBlocks < 1) {
                        nbBlocks = 1;
                    }
                } else {
                    pct = ((double) feeMed / value);
                    nbBlocks = ((Double) Math.ceil(pct * 6.0)).intValue();
                }
                tvEstimatedBlockWait.setText(nbBlocks + " blocks");
                setFee(value);

                if (value >= feeHigh) {
                    tvSelectedFeeRateLayman.setText("Urgent");

                } else if (i >= feeMed) {
                    tvSelectedFeeRateLayman.setText("Normal");

                } else if (i >= feeLow) {
                    tvSelectedFeeRateLayman.setText("Low");
                }


            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        switch (FEE_TYPE) {
            case FEE_LOW:
                FeeUtil.getInstance().setSuggestedFee(FeeUtil.getInstance().getLowFee());
                FeeUtil.getInstance().sanitizeFee();
//                tvEstimatedBlockWait.setText("24 blocks");

                break;
            case FEE_PRIORITY:
                FeeUtil.getInstance().setSuggestedFee(FeeUtil.getInstance().getHighFee());
                FeeUtil.getInstance().sanitizeFee();
//                tvEstimatedBlockWait.setText("2 blocks");
                break;
            default:
                FeeUtil.getInstance().setSuggestedFee(FeeUtil.getInstance().getNormalFee());
                FeeUtil.getInstance().sanitizeFee();

//                tvEstimatedBlockWait.setText("6 blocks");
                break;
        }

    }

    private void setFee(double fee) {

        double sanitySat = FeeUtil.getInstance().getHighFee().getDefaultPerKB().doubleValue() / 1000.0;
        final long sanityValue;
        if (sanitySat < 10.0) {
            sanityValue = 15L;
        } else {
            sanityValue = (long) (sanitySat * 1.5);
        }

        //        String val  = null;
        double d = FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().doubleValue() / 1000.0;
        NumberFormat decFormat = NumberFormat.getInstance(Locale.US);
        decFormat.setMaximumFractionDigits(3);
        decFormat.setMinimumFractionDigits(0);
        double customValue = 0.0;

        if (PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_TRUSTED_NODE, false)) {
            customValue = 0.0;
        } else {

            try {
                customValue = (double) fee;
            } catch (Exception e) {
                Toast.makeText(this, R.string.custom_fee_too_low, Toast.LENGTH_SHORT).show();
                return;
            }

        }
        SuggestedFee suggestedFee = new SuggestedFee();
        suggestedFee.setStressed(false);
        suggestedFee.setOK(true);
        suggestedFee.setDefaultPerKB(BigInteger.valueOf((long) (customValue * 1000.0)));
        FeeUtil.getInstance().setSuggestedFee(suggestedFee);
        prepareSpend();

    }

    private void setUpRicochet() {
        ricochetHopsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                SPEND_TYPE = SPEND_RICOCHET;
                PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_RICOCHET, true);
            } else {
                SPEND_TYPE = PrefsUtil.getInstance(this).getValue(PrefsUtil.SPEND_TYPE, SPEND_BOLTZMANN);
                PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_RICOCHET, false);
            }

        });
        ricochetHopsSwitch.setChecked(PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_RICOCHET, false));

    }

    private void setBalance() {

        try {
            balance = APIFactory.getInstance(this).getXpubAmounts().get(HD_WalletFactory.getInstance(this).get().getAccount(selectedAccount).xpubstr());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            balance = 0L;
        } catch (MnemonicException.MnemonicLengthException mle) {
            mle.printStackTrace();
            balance = 0L;
        } catch (java.lang.NullPointerException npe) {
            npe.printStackTrace();
            balance = 0L;
        }
        final String strAmount;
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(8);
        nf.setMinimumFractionDigits(1);
        nf.setMinimumIntegerDigits(1);

        strAmount = nf.format(balance / 1e8);

        tvMaxAmount.setOnClickListener(view -> {
            btcEditText.setText(strAmount);
        });

        tvMaxAmount.setText(strAmount + " " + getDisplayUnits());

    }

    private TextWatcher BTCWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            satEditText.removeTextChangedListener(satWatcher);
            btcEditText.removeTextChangedListener(this);

            try {
                if (editable.toString().length() == 0) {
                    satEditText.setText("0");
                    btcEditText.setText("");
                    satEditText.setSelection(satEditText.getText().length());
                    satEditText.addTextChangedListener(satWatcher);
                    btcEditText.addTextChangedListener(this);
                    return;
                }
                Float btc = Float.parseFloat(String.valueOf(editable));
                Double sats = getSatValue(Double.valueOf(btc));
                Log.i(TAG, "afterTextChanged: ....".concat(sats.toString()));
                satEditText.setText(formattedSatValue(sats));

                if (btc > 21000000.0) {
                    btcEditText.setText("0.00");
                    btcEditText.setSelection(btcEditText.getText().length());
                    satEditText.setText("0");
                    satEditText.setSelection(satEditText.getText().length());
                    Toast.makeText(SendActivity.this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
                }

//
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            satEditText.addTextChangedListener(satWatcher);
            btcEditText.addTextChangedListener(this);


        }
    };

    private String formattedSatValue(Object number) {
        DecimalFormat decimalFormat = new DecimalFormat("#,###");
        return decimalFormat.format(number).replace(",", " ");
    }

    private TextWatcher satWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            satEditText.removeTextChangedListener(this);
            btcEditText.removeTextChangedListener(BTCWatcher);

            try {
                if (editable.toString().length() == 0) {
                    btcEditText.setText("0.00");
                    satEditText.setText("");
                    satEditText.addTextChangedListener(this);
                    btcEditText.addTextChangedListener(BTCWatcher);
                    return;
                }
                String cleared_space = editable.toString().replace(" ", "");

                Double sats = Double.parseDouble(cleared_space);
                Float btc = getBtcValue(sats);
                String formatted = formattedSatValue(sats);
                Log.i(TAG, "afterTextChanged: ....".concat(btc.toString()));


                satEditText.setText(formatted);
                satEditText.setSelection(formatted.length());
                btcEditText.setText(String.format(Locale.ENGLISH, "%.8f", btc));
                if (btc > 21000000.0) {
                    btcEditText.setText("0.00");
                    btcEditText.setSelection(btcEditText.getText().length());
                    satEditText.setText("0");
                    satEditText.setSelection(satEditText.getText().length());
                    Toast.makeText(SendActivity.this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();

            }
            satEditText.addTextChangedListener(this);
            btcEditText.addTextChangedListener(BTCWatcher);

        }
    };

    private void setToAddress(String string) {
        tvToAddress.setText(string);
        toAddressEditText.setText(string);
    }

    private String getToAddress() {
        if (toAddressEditText.getText().toString().trim().length() != 0) {
            return toAddressEditText.getText().toString();
        }
        if (tvToAddress.getText().toString().length() != 0) {
            return tvToAddress.getText().toString();
        }
        return "";
    }

    private Float getBtcValue(Double sats) {
        return (float) (sats / 100000000);
    }

    private Double getSatValue(Double btc) {
        if (btc == 0) {
            return (double) 0;
        }
        return btc * 100000000;
    }

    private void review() {

        if (validateSpend() && prepareSpend()) {
            tvReviewSpendAmount.setText(btcEditText.getText());
            amountViewSwitcher.showNext();

            sendTransactionDetailsView.showReview(ricochetHopsSwitch.isChecked());

        }

    }

    private boolean prepareSpend() {

        double btc_amount = 0.0;

        try {
            btc_amount = NumberFormat.getInstance(Locale.US).parse(btcEditText.getText().toString().trim()).doubleValue();
//                    Log.i("SendFragment", "amount entered:" + btc_amount);
        } catch (NumberFormatException nfe) {
            btc_amount = 0.0;
        } catch (ParseException pe) {
            btc_amount = 0.0;
        }

        double dAmount = btc_amount;

        amount = (long) (Math.round(dAmount * 1e8));
        ;

//                Log.i("SendActivity", "amount:" + amount);
        address = strDestinationBTCAddress == null ? toAddressEditText.getText().toString().trim() : strDestinationBTCAddress;


        if ((FormatsUtil.getInstance().isValidBech32(address) || Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) || PrefsUtil.getInstance(SendActivity.this).getValue(PrefsUtil.USE_LIKE_TYPED_CHANGE, true) == false) {
            changeType = FormatsUtil.getInstance().isValidBech32(address) ? 84 : 49;
        } else {
            changeType = 44;
        }

        receivers = new HashMap<String, BigInteger>();
        receivers.put(address, BigInteger.valueOf(amount));

        // store current change index to restore value in case of sending fail
        change_index = 0;
        if (changeType == 84) {
            change_index = BIP84Util.getInstance(SendActivity.this).getWallet().getAccount(0).getChange().getAddrIdx();
        } else if (changeType == 49) {
            change_index = BIP49Util.getInstance(SendActivity.this).getWallet().getAccount(0).getChange().getAddrIdx();
        } else {
            try {
                change_index = HD_WalletFactory.getInstance(SendActivity.this).get().getAccount(0).getChange().getAddrIdx();
//                    Log.d("SendActivity", "storing change index:" + change_index);
            } catch (IOException ioe) {
                ;
            } catch (MnemonicException.MnemonicLengthException mle) {
                ;
            }
        }


        // if possible, get UTXO by input 'type': p2pkh, p2sh-p2wpkh or p2wpkh, else get all UTXO
        long neededAmount = 0L;
        if (FormatsUtil.getInstance().isValidBech32(address)) {
            neededAmount += FeeUtil.getInstance().estimatedFeeSegwit(0, 0, UTXOFactory.getInstance().getCountP2WPKH(), 4).longValue();
//                    Log.d("SendActivity", "segwit:" + neededAmount);
        } else if (Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
            neededAmount += FeeUtil.getInstance().estimatedFeeSegwit(0, UTXOFactory.getInstance().getCountP2SH_P2WPKH(), 0, 4).longValue();
//                    Log.d("SendActivity", "segwit:" + neededAmount);
        } else {
            neededAmount += FeeUtil.getInstance().estimatedFeeSegwit(UTXOFactory.getInstance().getCountP2PKH(), 0, 4).longValue();
//                    Log.d("SendActivity", "p2pkh:" + neededAmount);
        }
        neededAmount += amount;
        neededAmount += SamouraiWallet.bDust.longValue();

        // get all UTXO
        List<UTXO> utxos = SpendUtil.getUTXOS(this, address, neededAmount);

        List<UTXO> utxosP2WPKH = new ArrayList<UTXO>(UTXOFactory.getInstance().getP2WPKH().values());
        List<UTXO> utxosP2SH_P2WPKH = new ArrayList<UTXO>(UTXOFactory.getInstance().getP2SH_P2WPKH().values());
        List<UTXO> utxosP2PKH = new ArrayList<UTXO>(UTXOFactory.getInstance().getP2PKH().values());

        selectedUTXO = new ArrayList<UTXO>();
        long totalValueSelected = 0L;
        long change = 0L;
        BigInteger fee = null;

//                Log.d("SendActivity", "amount:" + amount);
//                Log.d("SendActivity", "balance:" + balance);

        // insufficient funds
        if (amount > balance) {
            Toast.makeText(SendActivity.this, R.string.insufficient_funds, Toast.LENGTH_SHORT).show();

        }
        // entire balance (can only be simple spend)
        else if (amount == balance) {
            // make sure we are using simple spend
            SPEND_TYPE = SPEND_SIMPLE;

//                    Log.d("SendActivity", "amount == balance");
            // take all utxos, deduct fee
            selectedUTXO.addAll(utxos);

            for (UTXO u : selectedUTXO) {
                totalValueSelected += u.getValue();
            }

//                    Log.d("SendActivity", "balance:" + balance);
//                    Log.d("SendActivity", "total value selected:" + totalValueSelected);

        } else {
            ;
        }

        boolean canDoBoltzmann = false;
        org.apache.commons.lang3.tuple.Pair<ArrayList<MyTransactionOutPoint>, ArrayList<TransactionOutput>> pair = null;
        if (SPEND_TYPE == SPEND_RICOCHET) {

            boolean samouraiFeeViaBIP47 = false;
            if (BIP47Meta.getInstance().getOutgoingStatus(BIP47Meta.strSamouraiDonationPCode) == BIP47Meta.STATUS_SENT_CFM) {
                samouraiFeeViaBIP47 = true;
            }

            ricochetJsonObj = RicochetMeta.getInstance(SendActivity.this).script(amount, FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().longValue(), address, 4, strPCode, samouraiFeeViaBIP47);
            if (ricochetJsonObj != null) {

                try {
                    long totalAmount = ricochetJsonObj.getLong("total_spend");
                    if (totalAmount > balance) {
                        Toast.makeText(SendActivity.this, R.string.insufficient_funds, Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    ricochetMessage = getText(R.string.ricochet_spend1) + " " + address + " " + getText(R.string.ricochet_spend2) + " " + Coin.valueOf(totalAmount).toPlainString() + " " + getText(R.string.ricochet_spend3);

                    btnSend.setText("send ".concat(Coin.valueOf(totalAmount).toPlainString()).concat(" BTC"));

                    return true;

                } catch (JSONException je) {
                    return false;
                }

            }

            return true;
        } else if (SPEND_TYPE == SPEND_BOLTZMANN) {

            Log.d("SendActivity", "needed amount:" + neededAmount);

            List<UTXO> _utxos1 = null;
            List<UTXO> _utxos2 = null;

            long valueP2WPKH = UTXOFactory.getInstance().getTotalP2WPKH();
            long valueP2SH_P2WPKH = UTXOFactory.getInstance().getTotalP2SH_P2WPKH();
            long valueP2PKH = UTXOFactory.getInstance().getTotalP2PKH();

            Log.d("SendActivity", "value P2WPKH:" + valueP2WPKH);
            Log.d("SendActivity", "value P2SH_P2WPKH:" + valueP2SH_P2WPKH);
            Log.d("SendActivity", "value P2PKH:" + valueP2PKH);

            boolean selectedP2WPKH = false;
            boolean selectedP2SH_P2WPKH = false;
            boolean selectedP2PKH = false;

            if ((valueP2WPKH > (neededAmount * 2)) && FormatsUtil.getInstance().isValidBech32(address)) {
                Log.d("SendActivity", "set 1 P2WPKH 2x");
                _utxos1 = utxosP2WPKH;
                selectedP2WPKH = true;
            } else if (!FormatsUtil.getInstance().isValidBech32(address) && (valueP2SH_P2WPKH > (neededAmount * 2)) && Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
                Log.d("SendActivity", "set 1 P2SH_P2WPKH 2x");
                _utxos1 = utxosP2SH_P2WPKH;
                selectedP2SH_P2WPKH = true;
            } else if (!FormatsUtil.getInstance().isValidBech32(address) && (valueP2PKH > (neededAmount * 2)) && !Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
                Log.d("SendActivity", "set 1 P2PKH 2x");
                _utxos1 = utxosP2PKH;
                selectedP2PKH = true;
            } else if (valueP2WPKH > (neededAmount * 2)) {
                Log.d("SendActivity", "set 1 P2WPKH 2x");
                _utxos1 = utxosP2WPKH;
                selectedP2WPKH = true;
            } else if (valueP2SH_P2WPKH > (neededAmount * 2)) {
                Log.d("SendActivity", "set 1 P2SH_P2WPKH 2x");
                _utxos1 = utxosP2SH_P2WPKH;
                selectedP2SH_P2WPKH = true;
            } else if (valueP2PKH > (neededAmount * 2)) {
                Log.d("SendActivity", "set 1 P2PKH 2x");
                _utxos1 = utxosP2PKH;
                selectedP2PKH = true;
            } else {
                ;
            }

            if (_utxos1 == null) {
                if (valueP2SH_P2WPKH > neededAmount) {
                    Log.d("SendActivity", "set 1 P2SH_P2WPKH");
                    _utxos1 = utxosP2SH_P2WPKH;
                    selectedP2SH_P2WPKH = true;
                } else if (valueP2WPKH > neededAmount) {
                    Log.d("SendActivity", "set 1 P2WPKH");
                    _utxos1 = utxosP2WPKH;
                    selectedP2WPKH = true;
                } else if (valueP2PKH > neededAmount) {
                    Log.d("SendActivity", "set 1 P2PKH");
                    _utxos1 = utxosP2PKH;
                    selectedP2PKH = true;
                } else {
                    ;
                }

            }

            if (_utxos1 != null && _utxos2 == null) {

                if (!selectedP2SH_P2WPKH && valueP2SH_P2WPKH > neededAmount) {
                    Log.d("SendActivity", "set 2 P2SH_P2WPKH");
                    _utxos2 = utxosP2SH_P2WPKH;
                } else if (!selectedP2WPKH && valueP2WPKH > neededAmount) {
                    Log.d("SendActivity", "set 2 P2WPKH");
                    _utxos2 = utxosP2WPKH;
                } else if (!selectedP2PKH && valueP2PKH > neededAmount) {
                    Log.d("SendActivity", "set 2 P2PKH");
                    _utxos2 = utxosP2PKH;
                } else {
                    ;
                }
            }

            if (_utxos1 == null && _utxos2 == null) {
                // can't do boltzmann, revert to SPEND_SIMPLE
                canDoBoltzmann = false;
                SPEND_TYPE = SPEND_SIMPLE;
            } else {

                Log.d("SendActivity", "boltzmann spend");

                Collections.shuffle(_utxos1);
                if (_utxos2 != null) {
                    Collections.shuffle(_utxos2);
                }

                // boltzmann spend (STONEWALL)
                pair = SendFactory.getInstance(SendActivity.this).boltzmann(_utxos1, _utxos2, BigInteger.valueOf(amount), address);

                if (pair == null) {
                    // can't do boltzmann, revert to SPEND_SIMPLE
                    canDoBoltzmann = false;
                    SPEND_TYPE = SPEND_SIMPLE;
                } else {
                    canDoBoltzmann = true;
                }
            }

        } else {
            ;
        }

        if (SPEND_TYPE == SPEND_SIMPLE && amount == balance) {
            // do nothing, utxo selection handles above
            ;
        }
        // simple spend (less than balance)
        else if (SPEND_TYPE == SPEND_SIMPLE) {
            List<UTXO> _utxos = utxos;

            // sort in ascending order by value
            Collections.sort(_utxos, new UTXO.UTXOComparator());
            Collections.reverse(_utxos);

            // get smallest 1 UTXO > than spend + fee + dust
            for (UTXO u : _utxos) {
                Triple<Integer, Integer, Integer> outpointTypes = FeeUtil.getInstance().getOutpointCount(new Vector(u.getOutpoints()));
                if (u.getValue() >= (amount + SamouraiWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFeeSegwit(outpointTypes.getLeft(), outpointTypes.getMiddle(), outpointTypes.getRight(), 2).longValue())) {
                    selectedUTXO.add(u);
                    totalValueSelected += u.getValue();
                    Log.d("SendActivity", "spend type:" + SPEND_TYPE);
                    Log.d("SendActivity", "single output");
                    Log.d("SendActivity", "amount:" + amount);
                    Log.d("SendActivity", "value selected:" + u.getValue());
                    Log.d("SendActivity", "total value selected:" + totalValueSelected);
                    Log.d("SendActivity", "nb inputs:" + u.getOutpoints().size());
                    break;
                }
            }

            if (selectedUTXO.size() == 0) {
                // sort in descending order by value
                Collections.sort(_utxos, new UTXO.UTXOComparator());
                int selected = 0;
                int p2pkh = 0;
                int p2sh_p2wpkh = 0;
                int p2wpkh = 0;

                // get largest UTXOs > than spend + fee + dust
                for (UTXO u : _utxos) {

                    selectedUTXO.add(u);
                    totalValueSelected += u.getValue();
                    selected += u.getOutpoints().size();

//                            Log.d("SendActivity", "value selected:" + u.getValue());
//                            Log.d("SendActivity", "total value selected/threshold:" + totalValueSelected + "/" + (amount + SamouraiWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFee(selected, 2).longValue()));

                    Triple<Integer, Integer, Integer> outpointTypes = FeeUtil.getInstance().getOutpointCount(new Vector<MyTransactionOutPoint>(u.getOutpoints()));
                    p2pkh += outpointTypes.getLeft();
                    p2sh_p2wpkh += outpointTypes.getMiddle();
                    p2wpkh += outpointTypes.getRight();
                    if (totalValueSelected >= (amount + SamouraiWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFeeSegwit(p2pkh, p2sh_p2wpkh, p2wpkh, 2).longValue())) {
                        Log.d("SendActivity", "spend type:" + SPEND_TYPE);
                        Log.d("SendActivity", "multiple outputs");
                        Log.d("SendActivity", "amount:" + amount);
                        Log.d("SendActivity", "total value selected:" + totalValueSelected);
                        Log.d("SendActivity", "nb inputs:" + selected);
                        break;
                    }
                }
            }

        } else if (pair != null) {

            selectedUTXO.clear();
            receivers.clear();

            long inputAmount = 0L;
            long outputAmount = 0L;

            for (MyTransactionOutPoint outpoint : pair.getLeft()) {
                UTXO u = new UTXO();
                List<MyTransactionOutPoint> outs = new ArrayList<MyTransactionOutPoint>();
                outs.add(outpoint);
                u.setOutpoints(outs);
                totalValueSelected += u.getValue();
                selectedUTXO.add(u);
                inputAmount += u.getValue();
            }

            for (TransactionOutput output : pair.getRight()) {
                try {
                    Script script = new Script(output.getScriptBytes());
                    receivers.put(script.getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString(), BigInteger.valueOf(output.getValue().longValue()));
                    outputAmount += output.getValue().longValue();
                } catch (Exception e) {
                    Toast.makeText(SendActivity.this, R.string.error_bip126_output, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            change = outputAmount - amount;
            fee = BigInteger.valueOf(inputAmount - outputAmount);

        } else {
            Toast.makeText(SendActivity.this, R.string.cannot_select_utxo, Toast.LENGTH_SHORT).show();
            return false;
        }

//         do spend here
        if (selectedUTXO.size() > 0) {

            // estimate fee for simple spend, already done if boltzmann
            if (SPEND_TYPE == SPEND_SIMPLE) {
                List<MyTransactionOutPoint> outpoints = new ArrayList<MyTransactionOutPoint>();
                for (UTXO utxo : selectedUTXO) {
                    outpoints.addAll(utxo.getOutpoints());
                }
                Triple<Integer, Integer, Integer> outpointTypes = FeeUtil.getInstance().getOutpointCount(new Vector(outpoints));
                if (amount == balance) {
                    fee = FeeUtil.getInstance().estimatedFeeSegwit(outpointTypes.getLeft(), outpointTypes.getMiddle(), outpointTypes.getRight(), 1);
                    amount -= fee.longValue();
                    receivers.clear();
                    receivers.put(address, BigInteger.valueOf(amount));

                    //
                    // fee sanity check
                    //
                    Transaction tx = SendFactory.getInstance(SendActivity.this).makeTransaction(0, outpoints, receivers);
                    tx = SendFactory.getInstance(SendActivity.this).signTransaction(tx);
                    byte[] serialized = tx.bitcoinSerialize();
                    Log.d("SendActivity", "size:" + serialized.length);
                    Log.d("SendActivity", "vsize:" + tx.getVirtualTransactionSize());
                    Log.d("SendActivity", "fee:" + fee.longValue());
                    if ((tx.hasWitness() && (fee.longValue() < tx.getVirtualTransactionSize())) || (!tx.hasWitness() && (fee.longValue() < serialized.length))) {
                        Toast.makeText(SendActivity.this, R.string.insufficient_fee, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    //
                    //
                    //

                } else {
                    fee = FeeUtil.getInstance().estimatedFeeSegwit(outpointTypes.getLeft(), outpointTypes.getMiddle(), outpointTypes.getRight(), 2);
                }
            }

            Log.d("SendActivity", "spend type:" + SPEND_TYPE);
            Log.d("SendActivity", "amount:" + amount);
            Log.d("SendActivity", "total value selected:" + totalValueSelected);
            Log.d("SendActivity", "fee:" + fee.longValue());
            Log.d("SendActivity", "nb inputs:" + selectedUTXO.size());

            change = totalValueSelected - (amount + fee.longValue());
//                    Log.d("SendActivity", "change:" + change);

            if (change > 0L && change < SamouraiWallet.bDust.longValue() && SPEND_TYPE == SPEND_SIMPLE) {

                AlertDialog.Builder dlg = new AlertDialog.Builder(SendActivity.this)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.change_is_dust)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                dialog.dismiss();

                            }
                        });
                if (!isFinishing()) {
                    dlg.show();
                }

                return false;
            }

            _change = change;
            final BigInteger _fee = fee;

            String dest = null;
            if (strPCode != null && strPCode.length() > 0) {
                dest = BIP47Meta.getInstance().getDisplayLabel(strPCode);
            } else {
                dest = address;
            }

            if (SendAddressUtil.getInstance().get(address) == 1) {
                strPrivacyWarning = getString(R.string.send_privacy_warning) + "\n\n";
            } else {
                strPrivacyWarning = "";
            }

            String strCannotDoBoltzmann = null;
            if (!canDoBoltzmann && PrefsUtil.getInstance(SendActivity.this).getValue(PrefsUtil.USE_BOLTZMANN, true) == true) {
                strCannotDoBoltzmann = getString(R.string.boltzmann_cannot) + "\n\n";
            } else {
                strCannotDoBoltzmann = "";
            }


            /*
                    String strNoLikedTypeBoltzmann = null;
                    if(canDoBoltzmann && PrefsUtil.getInstance(SendActivity.this).getValue(PrefsUtil.USE_BOLTZMANN, true) == true && PrefsUtil.getInstance(SendActivity.this).getValue(PrefsUtil.USE_LIKE_TYPED_CHANGE, true) == false)    {
                        strNoLikedTypeBoltzmann = getString(R.string.boltzmann_like_typed) + "\n\n";
                    }
                    else    {
                        strNoLikedTypeBoltzmann = "";
                    }
                    */

//                    String message = strCannotDoBoltzmann + strNoLikedTypeBoltzmann + strPrivacyWarning + "Send " + Coin.valueOf(amount).toPlainString() + " to " + dest + " (fee:" + Coin.valueOf(_fee.longValue()).toPlainString() + ")?\n";
            message = strCannotDoBoltzmann + strPrivacyWarning + "Send " + Coin.valueOf(amount).toPlainString() + " to " + dest + " (fee:" + Coin.valueOf(_fee.longValue()).toPlainString() + ")?\n";

            tvTotalFee.setText(Coin.valueOf(_fee.longValue()).toPlainString().concat(" BTC"));

            double value = Double.parseDouble(String.valueOf(_fee.add(BigInteger.valueOf(amount))));

            btnSend.setText("send ".concat(String.valueOf(getBtcValue(value)).concat(" BTC")));

            return true;
        }
        return false;
    }

    private void initiateSpend() {

        if (SPEND_TYPE == SPEND_RICOCHET) {

            ricochetSpend();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(SendActivity.this);
        builder.setTitle(R.string.app_name);
        builder.setMessage(message);
        final CheckBox cbShowAgain;
        if (strPrivacyWarning.length() > 0) {
            cbShowAgain = new CheckBox(SendActivity.this);
            cbShowAgain.setText(R.string.do_not_repeat_sent_to);
            cbShowAgain.setChecked(false);
            builder.setView(cbShowAgain);
        } else {
            cbShowAgain = null;
        }
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, int whichButton) {

                final List<MyTransactionOutPoint> outPoints = new ArrayList<MyTransactionOutPoint>();
                for (UTXO u : selectedUTXO) {
                    outPoints.addAll(u.getOutpoints());
                }

                // add change
                if (_change > 0L) {
                    if (SPEND_TYPE == SPEND_SIMPLE) {
                        if (changeType == 84) {
                            String change_address = BIP84Util.getInstance(SendActivity.this).getAddressAt(AddressFactory.CHANGE_CHAIN, BIP84Util.getInstance(SendActivity.this).getWallet().getAccount(0).getChange().getAddrIdx()).getBech32AsString();
                            receivers.put(change_address, BigInteger.valueOf(_change));
                        } else if (changeType == 49) {
                            String change_address = BIP49Util.getInstance(SendActivity.this).getAddressAt(AddressFactory.CHANGE_CHAIN, BIP49Util.getInstance(SendActivity.this).getWallet().getAccount(0).getChange().getAddrIdx()).getAddressAsString();
                            receivers.put(change_address, BigInteger.valueOf(_change));
                        } else {
                            try {
                                String change_address = HD_WalletFactory.getInstance(SendActivity.this).get().getAccount(0).getChange().getAddressAt(HD_WalletFactory.getInstance(SendActivity.this).get().getAccount(0).getChange().getAddrIdx()).getAddressString();
                                receivers.put(change_address, BigInteger.valueOf(_change));
                            } catch (IOException ioe) {
                                Toast.makeText(SendActivity.this, R.string.error_change_output, Toast.LENGTH_SHORT).show();
                                return;
                            } catch (MnemonicException.MnemonicLengthException mle) {
                                Toast.makeText(SendActivity.this, R.string.error_change_output, Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                    } else if (SPEND_TYPE == SPEND_BOLTZMANN) {
                        // do nothing, change addresses included
                        ;
                    } else {
                        ;
                    }
                }

                SendParams.getInstance().setParams(outPoints,
                        receivers,
                        strPCode,
                        SPEND_TYPE,
                        _change,
                        changeType,
                        address,
                        strPrivacyWarning.length() > 0,
                        cbShowAgain != null ? cbShowAgain.isChecked() : false,
                        amount,
                        change_index
                );
                Intent _intent = new Intent(SendActivity.this, TxAnimUIActivity.class);
                startActivity(_intent);

            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, int whichButton) {

                SendActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                            btSend.setActivated(true);
//                            btSend.setClickable(true);
//                                        dialog.dismiss();
                    }
                });

            }
        });

        AlertDialog alert = builder.create();
        alert.show();

    }

    private void ricochetSpend() {

        AlertDialog.Builder dlg = new AlertDialog.Builder(SendActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(ricochetMessage)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        RicochetMeta.getInstance(SendActivity.this).add(ricochetJsonObj);

                        dialog.dismiss();

                        Intent intent = new Intent(SendActivity.this, RicochetActivity.class);
                        startActivityForResult(intent, RICOCHET);

                    }

                }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        dialog.dismiss();

                    }
                });
        if (!isFinishing()) {
            dlg.show();
        }

    }

    private void backToTransactionView() {
        amountViewSwitcher.showPrevious();
        sendTransactionDetailsView.showTransaction();

    }

    @Override
    public void onBackPressed() {
        if (sendTransactionDetailsView.isReview()) {
            backToTransactionView();
        } else {
            super.onBackPressed();
        }
    }

    private void enableAmount(boolean enable) {
        btcEditText.setEnabled(enable);
        satEditText.setEnabled(enable);
    }

    private void processScan(String data) {

        if (data.contains("https://bitpay.com")) {

            AlertDialog.Builder dlg = new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.no_bitpay)
                    .setCancelable(false)
                    .setPositiveButton(R.string.learn_more, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://blog.samouraiwallet.com/post/169222582782/bitpay-qr-codes-are-no-longer-valid-important"));
                            startActivity(intent);

                        }
                    }).setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            dialog.dismiss();

                        }
                    });
            if (!isFinishing()) {
                dlg.show();
            }

            return;
        }

        if (FormatsUtil.getInstance().isValidPaymentCode(data)) {
            processPCode(data, null);
            return;
        }

        if (FormatsUtil.getInstance().isBitcoinUri(data)) {
            String address = FormatsUtil.getInstance().getBitcoinAddress(data);
            String amount = FormatsUtil.getInstance().getBitcoinAmount(data);

            setToAddress(address);
            if (amount != null) {
                try {
                    NumberFormat btcFormat = NumberFormat.getInstance(Locale.US);
                    btcFormat.setMaximumFractionDigits(8);
                    btcFormat.setMinimumFractionDigits(1);
//                    setToAddress(btcFormat.format(Double.parseDouble(amount) / 1e8));
//                    Log.i(TAG, "------->: ".concat();
                    btcEditText.setText(btcFormat.format(Double.parseDouble(amount) / 1e8));
                } catch (NumberFormatException nfe) {
//                    setToAddress("0.0");
                }
            }

            final String strAmount;
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            nf.setMinimumIntegerDigits(1);
            nf.setMinimumFractionDigits(1);
            nf.setMaximumFractionDigits(8);
            strAmount = nf.format(balance / 1e8);
            tvMaxAmount.setText(strAmount + " " + getDisplayUnits());

            try {
                if (amount != null && Double.parseDouble(amount) != 0.0) {
                    toAddressEditText.setEnabled(false);
                    selectPaynymBtn.setEnabled(false);
                    selectPaynymBtn.setAlpha(0.5f);
                    //                    Toast.makeText(this, R.string.no_edit_BIP21_scan, Toast.LENGTH_SHORT).show();
                    enableAmount(false);

                }
            } catch (NumberFormatException nfe) {
                enableAmount(true);
            }

        } else if (FormatsUtil.getInstance().isValidBitcoinAddress(data)) {

            if (FormatsUtil.getInstance().isValidBech32(data)) {
                setToAddress(data.toLowerCase());
            } else {
                setToAddress(data);
            }

        } else if (data.contains("?")) {

            String pcode = data.substring(0, data.indexOf("?"));
            // not valid BIP21 but seen often enough
            if (pcode.startsWith("bitcoin://")) {
                pcode = pcode.substring(10);
            }
            if (pcode.startsWith("bitcoin:")) {
                pcode = pcode.substring(8);
            }
            if (FormatsUtil.getInstance().isValidPaymentCode(pcode)) {
                processPCode(pcode, data.substring(data.indexOf("?")));
            }
        } else {
            Toast.makeText(this, R.string.scan_error, Toast.LENGTH_SHORT).show();
        }

        validateSpend();
    }

    public String getDisplayUnits() {

        return MonetaryUtil.getInstance().getBTCUnits();

    }

    private void processPCode(String pcode, String meta) {

        if (FormatsUtil.getInstance().isValidPaymentCode(pcode)) {

            if (BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_SENT_CFM) {
                try {
                    PaymentCode _pcode = new PaymentCode(pcode);
                    PaymentAddress paymentAddress = BIP47Util.getInstance(this).getSendAddress(_pcode, BIP47Meta.getInstance().getOutgoingIdx(pcode));

                    if (BIP47Meta.getInstance().getSegwit(pcode)) {
                        SegwitAddress segwitAddress = new SegwitAddress(paymentAddress.getSendECKey(), SamouraiWallet.getInstance().getCurrentNetworkParams());
                        strDestinationBTCAddress = segwitAddress.getBech32AsString();
                    } else {
                        strDestinationBTCAddress = paymentAddress.getSendECKey().toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                    }

                    strPCode = _pcode.toString();
                    setToAddress(BIP47Meta.getInstance().getDisplayLabel(strPCode));
                    toAddressEditText.setEnabled(false);
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_payment_code, Toast.LENGTH_SHORT).show();
                }
            } else {
//                Toast.makeText(SendActivity.this, "Payment must be added and notification tx sent", Toast.LENGTH_SHORT).show();

                if (meta != null && meta.startsWith("?") && meta.length() > 1) {
                    meta = meta.substring(1);
                }

                Intent intent = new Intent(this, BIP47Activity.class);
                intent.putExtra("pcode", pcode);
                if (meta != null && meta.length() > 0) {
                    intent.putExtra("meta", meta);
                }
                startActivity(intent);
            }

        } else {
            Toast.makeText(this, R.string.invalid_payment_code, Toast.LENGTH_SHORT).show();
        }

    }

    private boolean validateSpend() {

        boolean isValid = false;
        boolean insufficientFunds = false;

        double btc_amount = 0.0;

        String strBTCAddress = getToAddress();
        if (strBTCAddress.startsWith("bitcoin:")) {
            setToAddress(strBTCAddress.substring(8));
        }
        setToAddress(strBTCAddress);

        try {
            btc_amount = NumberFormat.getInstance(Locale.US).parse(btcEditText.getText().toString()).doubleValue();
//            Log.i("SendFragment", "amount entered:" + btc_amount);
        } catch (NumberFormatException nfe) {
            btc_amount = 0.0;
        } catch (ParseException pe) {
            btc_amount = 0.0;
        }

        final double dAmount = btc_amount;

        //        Log.i("SendFragment", "amount entered (converted):" + dAmount);

        final long amount = (long) (Math.round(dAmount * 1e8));
//        Log.i("SendFragment", "amount entered (converted to long):" + amount);
//        Log.i("SendFragment", "balance:" + balance);
        if (amount > balance) {
            insufficientFunds = true;
        }

//        Log.i("SendFragment", "insufficient funds:" + insufficientFunds);

        if (btc_amount > 0.00 && FormatsUtil.getInstance().isValidBitcoinAddress(getToAddress())) {
            isValid = true;
        } else if (btc_amount > 0.00 && strDestinationBTCAddress != null && FormatsUtil.getInstance().isValidBitcoinAddress(strDestinationBTCAddress)) {
            isValid = true;
        } else {
            isValid = false;
        }

        if (insufficientFunds) {
            Toast.makeText(this, getString(R.string.insufficient_funds), Toast.LENGTH_SHORT).show();
        }
        if (!isValid || insufficientFunds) {
            return false;
        } else {
            return true;
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK && requestCode == SCAN_QR) {

            if (data != null && data.getStringExtra(ZBarConstants.SCAN_RESULT) != null) {

                strPCode = null;
                strDestinationBTCAddress = null;

                final String strResult = data.getStringExtra(ZBarConstants.SCAN_RESULT);

                processScan(strResult);

            }
        } else if (resultCode == Activity.RESULT_CANCELED && requestCode == SCAN_QR) {
            ;
        } else if (resultCode == Activity.RESULT_OK && requestCode == RICOCHET) {
            ;
        } else if (resultCode == Activity.RESULT_CANCELED && requestCode == RICOCHET) {
            ;
        } else {
            ;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.send_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (item.getItemId() == android.R.id.home) {
            this.onBackPressed();
            return true;
        }
        // noinspection SimplifiableIfStatement
        if (id == R.id.action_scan_qr) {
            doScan();
        } else if (id == R.id.action_ricochet) {
            Intent intent = new Intent(SendActivity.this, RicochetActivity.class);
            startActivity(intent);
        } else if (id == R.id.action_empty_ricochet) {
            emptyRicochetQueue();
        } else if (id == R.id.action_utxo) {
            doUTXO();
        } else if (id == R.id.action_fees) {
            doFees();
        } else if (id == R.id.action_batch) {
            doBatchSpend();
        } else if (id == R.id.action_support) {
            doSupport();
        } else {
            ;
        }

        return super.onOptionsItemSelected(item);
    }

    private void emptyRicochetQueue() {

        RicochetMeta.getInstance(this).setLastRicochet(null);
        RicochetMeta.getInstance(this).empty();

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    PayloadUtil.getInstance(SendActivity.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(SendActivity.this).getGUID() + AccessFactory.getInstance(SendActivity.this).getPIN()));
                } catch (Exception e) {
                    ;
                }

            }
        }).start();

    }

    private void doScan() {
        Intent intent = new Intent(SendActivity.this, ZBarScannerActivity.class);
        intent.putExtra(ZBarConstants.SCAN_MODES, new int[]{Symbol.QRCODE});
        startActivityForResult(intent, SCAN_QR);
    }

    private void doSupport() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://support.samourai.io/section/8-sending-bitcoin"));
        startActivity(intent);
    }

    private void doUTXO() {
        Intent intent = new Intent(SendActivity.this, UTXOActivity.class);
        startActivity(intent);
    }

    private void doBatchSpend() {
        Intent intent = new Intent(SendActivity.this, BatchSendActivity.class);
        startActivity(intent);
    }

    private void doFees() {

        SuggestedFee highFee = FeeUtil.getInstance().getHighFee();
        SuggestedFee normalFee = FeeUtil.getInstance().getNormalFee();
        SuggestedFee lowFee = FeeUtil.getInstance().getLowFee();

        String message = getText(R.string.current_fee_selection) + " " + (FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().longValue() / 1000L) + " " + getText(R.string.slash_sat);
        message += "\n";
        message += getText(R.string.current_hi_fee_value) + " " + (highFee.getDefaultPerKB().longValue() / 1000L) + " " + getText(R.string.slash_sat);
        message += "\n";
        message += getText(R.string.current_mid_fee_value) + " " + (normalFee.getDefaultPerKB().longValue() / 1000L) + " " + getText(R.string.slash_sat);
        message += "\n";
        message += getText(R.string.current_lo_fee_value) + " " + (lowFee.getDefaultPerKB().longValue() / 1000L) + " " + getText(R.string.slash_sat);

        AlertDialog.Builder dlg = new AlertDialog.Builder(SendActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, whichButton) -> dialog.dismiss());
        if (!isFinishing()) {
            dlg.show();
        }

    }

}

