package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;
import java.util.Map;

/**
 * A stake delegation registry manages a list of registered pools, is the endpoint
 * for delegators/pool owners to interact with different pools.
 * <p>
 * Workflow for pool operator:
 * - Register a staker;
 * - Register the staker as a pool;
 * - Add the pool registry as a listener to the staker;
 * - Set the coinbase address to the address of the pool coinbase contract.
 */
public class PoolRegistry {

    // TODO: replace object graph-based collections with key-value storage
    // TODO: replace long with BigInteger
    // TODO: add meta data and commission rate setters/getters
    // TODO: add events

    public static final BigInteger MIN_SELF_STAKE = BigInteger.valueOf(1000L);

    @Initializable
    private static Address stakerRegistry;

    private static byte[] poolCoinbaseContract;
    private static byte[] poolCustodianContract;

    private static Map<Address, PoolState> pools = new AionMap<>();

    static {
        poolCoinbaseContract = hexStringToByteArray("00000881504b0304140008080800d4ad282b000000000000000000000000140004004d4554412d494e462f4d414e49464553542e4d46feca0000f34dcccb4c4b2d2ed10d4b2d2acecccfb35230d433e0e5f24dccccd375ce492c2eb65270e4e5e2e50200504b07082bc2bf352a00000028000000504b0304140008080800d4ad282b00000000000000000000000007000000412e636c6173736d52db4e1351145da72d4c3b0c721750b4de9d2942b1222a37a10594522e52c335c69c0e873230b43a336de27ff8e28bafbe822675a2c6474dfc278dfb1422d430c9cc9cb36f6bedb5f7af3f5fbe03184296814d2a600ccdbbbccce3362fe4e38bb95d617a0a820c2d4bc5a29d2a5a851c7745bf0ca1047a1b33bcbc1f9fdcda7284eb8e509c5e63481b2b0ce7a4256917cd3d73875b05051186485e78296edbc26168d28d9a240d0dd054a868d4500f2582009a181a4e85286861a817af4bdc76193af4ccff9c478c0d0d6d6857d18a0e06c5a158cb110c217dc358d1d0892e09d0cdd05e4ddde7de4e3c69e5670b9ec80b47c1454a2a73bb2416b719baf5b491392b8ea85ec265153d8832b4524bcb629f3ab40af9e98270f26f1882ba91d67015d724dc75c237a96986c15a99ce2ebe994c1f09b32cdc92ed11d84ddc92756e33a827760506e969b9d9926952b12a2675df8b3b2a62e863088f9a3691f2c6ab2e1a084b2ab84b0d12e129ee71a98ab199d4700f83b2fc7dd276f438a14edf4c4ac11ee0a18a041e51725ec3889c4f02a39429fb25638e94d48d5373c87a0ec9409c1f6342c64ed66cd69157418ad8790e2fb8dbc209635ac5941c17131a9e4ac91298952b10566905e6889523cac2f134cc6340125d20fc54718bc6da98f5b8b937cf5f3de7399bee6ab658724c3163d982c40f20444b4e56b94e740a2222ab9265896eaba8abfac77c9cfb88e60acefbb810fb0125f401a1209da391b7ef4f6eb1a88f2bc16f611f377ce80b7d15f493f5b00af08cbe6184da279836419d218e8163902881caa7ed2b12eb3e863e6378bd82b14f685e3dac3a2218c7e071f01ab10bd07ff824782e564132d3fb130a411ff4b6ce54f0e41d3a63b2c84bfaa45facadfac848a78f4576f08f8e86c06f7429482898ea21db32d902a8fb0b504b070805b9dd0d9f020000f9030000504b0304140008080800d4ad282b00000000000000000000000007000000422e636c6173736d545d4f1351103db7bb65b76591adf2dd455a40694bf9100444c040312698a20f3524950759da8a4528a414222f461ff805fc001a124362248104ac11823efbeebf21e2dcedb6dd429beede99b973cfcc9999bb7ffefdbc00308c09061692c018d4657d4bef5bd1534b7d2f179713b18c0481a1762a34f334115b8b27d2bddc81dc17e99911e8f41509312ec824c4196cf32112120c55e3c95432f384c1ee9b0ff9e718049f7f4ec12da84e887029a886e2800d7714d440e2523d439dcf1f2ea510c9a493a9a531c27baba0098d4e726a2e4b32ef21c16dc0478cf773f27fa7a08d07b2c143dab48476273af241ef72eb3db2261574e57d7c0a1c68e552a01c7d7b23935895106470e8e9b4be1d5b5bdf6608f8c2d7cb343653c134c3f9f6a2cf891ef473e6b54e6818a0d04b545362aa6fadf64dc5e3e9c4c60691acb6a812869d18e1be35d36ba98d8c9ecacce92b9b5456719ada40e648468fbd9fd5d75fe98b2ba43b236b9be958e2597225012f1111a923225a5005ea2b4649b34126dd61d139616a8221530b6875d01ea549efc7a4b9c96ea3b53690c3ed40f729ea02c2291a8ec17fd460349a8ea3a6632b0b9ca0ee0b1481af17599a89f001e4c077b484bb8f8c4363f476427089971025aa868d57be08231a303dfc343d0d83aafc6177dc5d50c5854117ae3e7eda1d0f4449b52f5032ee6f46369cc85d13e637ece003796285d9996cbb06b3f35995b74b567b456b5545ab54d12a57b4aa9521d422c6a4414695399bb643b3b63545361162c3689da026788db2f2e371d5b18fea33744473e8fc65807294d722471954abf62c9b39dc3f2cd69e2aecd6486e2a36ef2f05e0d3f2d50890839f854b851bc8c263a99beacd422d15ff005b37427bf7caf3caa76bdf2f4ba87ba2b4a559b6b473f945215c8fa0fdc003035be3d067d0a23d390c5a27a919f64b0cd9b5fa4bb4b02b74c226f1bf66383cc49049f1c89c88dd02c53282ed5682ed591af522413ebe6f6e506caf4851f55888a89e7379b6801b14544f818aeac973198906737864e5520d71486bb844974417dfc63f5f66f6fda4f10968b97ebb8ad18eaded755109f887cd3c1d344f37566e46e9a800d640e2b8719becff01504b070823d52851280300001c060000504b0304140008080800d4ad282b00000000000000000000000007000000432e636c6173733d4ebb0ac240109c358991181f69d3d9a985e9ac44d0a0a0a542fa239e72122fa217f1b7ac040b3fc08f12d7082eec6386d9d97dbd1f4f004304048a5d1021dc8b8b8832a177d1aad0461de4ec9acaa351b976611182c974f167065f31af6e39970e7ec126d591d2ca8c0956b797f8f050f760c32734e25c9f8dd02611592109769c6fb879ebbc38a572ae32890e2aac651b84a8827f42935105359ee86bc5b5c54c5062c0e9dfd1b8fd2ea35d4a9d0f504b0708d8f1b600b0000000d8000000504b01021400140008080800d4ad282b2bc2bf352a000000280000001400040000000000000000000000000000004d4554412d494e462f4d414e49464553542e4d46feca0000504b01021400140008080800d4ad282b05b9dd0d9f020000f9030000070000000000000000000000000070000000412e636c617373504b01021400140008080800d4ad282b23d52851280300001c060000070000000000000000000000000044030000422e636c617373504b01021400140008080800d4ad282bd8f1b600b0000000d80000000700000000000000000000000000a1060000432e636c617373504b05060000000004000400e500000086070000000000000021220000000000000000000000000000000000000000000000000000000000000000");
        poolCustodianContract = hexStringToByteArray("0000145c504b0304140008080800d4ad282b000000000000000000000000140004004d4554412d494e462f4d414e49464553542e4d46feca0000f34dcccb4c4b2d2ed10d4b2d2acecccfb35230d433e0e5f24dccccd375ce492c2eb65270e4e5e2e50200504b07082bc2bf352a00000028000000504b0304140008080800d4ad282b00000000000000000000000007000000412e636c6173737d95eb531b5518c69f03099b6c9672292d0d2297aa980b04adb55aa0b524502d0d5001a180b52ec91202cb06369bd8e2dd7abf7ef1838e337ef593338833e98eedf8b1cef83fe9f8bebbcb25140b939cdd739e3dcfefbd9ccddffffef1278073f84e400c4b10028dab6a59edd75523d73fb9b4aa652c09b502cdd70a053d552a5a856c5e3512aca127e8539f56cbebfdc3d9aca9158b8334b724d014a99a1c8bced2fcb2406d243aab404648460d149ad3047c91059e3c86069e6ca4c91109cd02754379236f5d54d0c2f2e33841d272c1d20268258d29d01a49ef934e5b66dec80d46d323830ac268e3271e235d91c8ab615cc9e3e860492749369921ba9854d08dd33cf9848200a420d13c25d0e278acabd64a7f329fbb62585a4e33253c2d209555bda44d5258e1c858347d948e8ca288c988202e708c29927a21b3965951f386843e62cb69d694b64eb7443f6a6866eeb693a53105fd78464602cf12614ee06c751047bb2d26898365535ab1a45be4fe1cce72569f17e8385492ea9c8c09044a06a777a610c08be4b94115e0b038590318e4bc0c51a21646a726054efe5fb417f15290a2bd441b24252405e47d1c0923d42d4ec056c93446544b557099533e8a9705fc91c52437420baec8488188c49a823467218571bacb08c40f65e1d1113558a66a14973573da52d79cc05ea56db25c6ea7231b97f386aae7b7b4194f17c06b545653db2ce54d4dc11cb76402d70582449d52755da3b66b8844ab9c142c609175af2ba873dbe60d81d0018984372999b4abaa533b9e38d8b6ee011b8c2e28584246860ac6cb90958265ae5d0254fc60be385dca64682ba739489cc72aa76d8dca3694d19d8342e444e966d5e0ac2650a070d7156c325f0ac4eee34ea3c915eaeb48f4e1e3a3a084326bdfaa7a0db8ab126ecbd84296c30c7257bdc3e7e405be7a8ff64c49f880ab38e61ca5017c24e343dc5130896b2cf944c13466f8ea334a86a99535d352f0059fed04be24b454214b6f837aaa55666d5cdd98519774ba97a70b2533a35dceeb1a1dcf1af8e86555436f10ca345dc97c50690cd23f4141e06bba5b84dfd10ddba88f3d80e4fb05be5a1b4d9dc1ef7fdebfbb87e3f37771b2f95405edb10aba2a787262073d9d367afb6c9cb1716eee37da44e01bfa0ec017ba2414eaeb20c7ec19dd21a33a1a971f69d4f7e061d3f39e69670517fa5cf3011fb9ef6038ec73dd07fcf7909a0ffb2b78e52eae5630f1eb1e4d08752e8dec0051863da01f0888f371eb1050fcc07557155cd87704dd944717a70fa15d08fb3c44bf87e8f710eb1cc4ba23108f211072f88832e45052f53dca3344594b630f51ee5ace9265075991cbb86bd27ba0085c69fa81f09e6fa251d0e86fb331bfbb4c3f2ade72981a84ffea6ddcd8c1cd0a340a6d57462f444fd6e515af2516efedb6b132e0e75ceb9c13ffb6a30e607d6fd31e871868e3886d6c50bcf315147fc74d77e8f13883b070da7be63ed582517eda7fe62aa5f5563afe17a4dafb81ed3837dfdb3fa235c69bf04e1337aecfd978d75d3cef2cb61f5c742e49f1be8d8f4931e528baf71487659fbab25947d61cdb33f89c0d6c7c25b6f70ad600ff3f3825818ef1567b774f2b4d7feb9c35ff7f504b0708d51c32f69104000023080000504b0304140008080800d4ad282b00000000000000000000000007000000422e636c6173736d545b4f135110feceee96dd9645b60ae5d22205514a690141f0d2a25cd4a4a6e8430d09f2204b5bb1505a520a9117a30ffc027e0084c490184920016b84a0cfbefb6f8838677bdb429beeeecc9cb97ddfccee9f7f3fcf008c609c814dc8600cdaa2beaef727f5d442ffabf9c578342b4364a81f9f083d8d47d3b178a68f3b907b94ae9048d11724c4b8a09010671066274878c750134ca412d9c70c16cfec44cf3483e8e99956710d9a0d12ec2a6aa15a21e0868a3ac85c6aa4c0f70c0d9e9e70b98d483693482d04e828a9a205cd36726cad6834ef21c3453e09a34c84a445437a41d2125d2915b77861015da43d9371c7866ede841b1ddcda43d6b48adebc8f4fc54db473a9afb2d2c66a36be2c6380c1aa6732fa4634bdb2c1e0f5842fd3160855318538fe410cd97017f73813f536b461844a2f13c7845a5f5fee1f8fc532f1d555025c6b52653cb221c07deb26d3a9d5ac9eca4eebc935e25b9aa4b1903992d5a34b53faca6b7d3e49ba2d925ecb44e3cf13c9383a0888441392d08a1ad09c314a9a008574ab49e7806928864c23a1a795cea84dba3f26cd4976819ef5de1cae7b7b8fd1e0158fe13804ff3134a1b9e0f8b0e0d8c6bc4768f80255e4cfb31dda91f01e14ef7738c3bd0746d013badb20daa5734832b12170e64b6924238d9f47d3e518d2940f5b41675195e686ecb8f8f8692be89d21d53247cd38bf19dd087cb08534bf61015fd023739acdb1f64b69363f6bca46d96aa96aada96a95ab5a95aa56ad7a0aad9463cc00a3291c4dfb7e81dbce129a08a161f41ca521dc3668e5e131cdba8bda1374cfe4e0f96524e559de483ccb9056b36d3accc1bb5fe29e1876ba486e290def2f15e0dbf2d52890839f85cbc40deec06de24debd88156267f0feb574a776c57f6956fd7b25bd150ff68f9c8653a729d2a2f8be5fca2eb07868ddc2e9efa046d33fe1cee9b37a90596730c5b5c8de7f48db840170499ffdb0c8707b450798807858dd82a42ac00d86906d8b943ab5e02c8d7f7ed15889d55216a6e1310cd7daa4c15f3fa44cd5d84a2b9f3580233be1c82662cb590865d8e7374cbf4e20bfcf355e87e8034be01ad97dfae52b543f378ed4401ffb015a27d85e8a6eac328878a600e12c78cb7c9f21f504b0708c7617e49330300002c060000504b0304140008080800d4ad282b00000000000000000000000007000000432e636c6173733bf56fd73e06060633060146064667760646460681acc4b244fd9cc4bc747dffa4acd4e41276066646067e47274fd7bce4fc94d4223d9002a0f27420f66406eafe0f64143032b06a7869463bb13240005090d7393fafb82431af242c31a734959181c519a89d91812b38bfb42839d52d332795c19081898105a89a91418a818d01683f032f90c7c4c001647102211790e6038a3400654156950870eee1f0d56116600bd1619113b0a81661e0aeef9818a2c32a276000e7b0c90968c039ec72020a700e879c80049c2300542780e001157220781c7230e606b07f80410076192b00504b0708da5ceda9e100000033010000504b0304140008080800d4ad282b00000000000000000000000007000000442e636c6173736d535d535251145d878b5ec5ab22667e961f655e3e92b4b20f4c0385c2204b4c327b39e21dc3100cae36f4d28fe807f4d2d46bce143ad5d07b3fa969aabdb9d7344706eed967b1f65eebecb3ef8fdf5faa0026f04440ccaa1002ee0db9238339995f0fceaf6e1819538522d0158ec45366d1909bd9fc7a349f29ac19c5516652de330125110b5194a55f5ca1820e0a3638a0d4fac96c3e6b4e1149f72e6968468b0b4eb42a10a0af88a9f0d0b24d443deea53a1a4ea1c385769cd6500fb5110e7411e1858053f7ae44041c72b5562caea10f6798799641a96100bdbc1dfcef14a972c93436559c136894c5a22c670a5b65019f9e387ed250fc0428cea68771c185f31811a8cbe40c59a455b7bc7ae163493f39dce223cc7913b3dc8a97bc89589c202e31678ce0b2cd61f832ae307c95602ad879d40fb59afa1cb26ab98fe32a6e0834ac1b66a46c1a250d213ef64d4c522193bb54b33c856917b5ee3681af583465894630c3a2b304ef08a8fa8aed31863b8cdf25bc24e0d113726733185e5b2b1aa592eda3e908a4224180596007616eaa86fbec228979aa1055f1d08505b4d2bdac44542c0a34cf14f22553e6cd2599db36c8e50c8d10c12953669e27e5d6a25ccdd1de952a6c1733462c9b333046f69d3c43b4d22050d4830634d2d02cf188c145fba6237b1e17c1f345cf748dede019438b6f0f6e9fa76d1f9d9fd1bd0bfe08fab7d7e68da10e3cabc3be4fe8aea07fb2f75b43d28a87948012a8ee43b7b68174e0a39d7e11a376fa2025737a0773dcf5158ca739eaaf6022ed3ba05fc3759bfe945c313d26fcefd153cb193fc8710f72f4017e7f05b7922c1c3e84390a54df5410b5c20ae2567d81c7f474c2d13742f11ceed94a195b297a92d290a5e423a50749f740a0fa164d5fb1b0bc87d4f743c249421a94be5f6857f1e8273c74093468b6e0b0dd70774fdbeb3fef0eebedfecba597be95c2e5da7dd5fd05504b0708aadb7187be0200007e040000504b0304140008080800d4ad282b00000000000000000000000007000000452e636c6173733d4ecb0ec150103da32f2945b7767658e8ce4a2408094b12fb9bba914bdd0ab7e2b7ac24163ec04789699b98641ee7e4cc99f97c5f6f004384049a7b2042fb206e224a84de47eb4c1b7592f37b2ccf46a5da83450827d3e59f19e4625e3d72ae1c94c126ee486965c604abdbdbd6e1a3e6c3469d10cc527d35429bad483249b067e98e9bbf49b34b2c172a91e8a0c25ab6411b2ef82734185550e589722bae4d66c202034eff89e0515e46ab903a3f504b070853839b74af000000d8000000504b0304140008080800d4ad282b00000000000000000000000007000000462e636c61737375555d731365147edecdd736049aa6144ad3d080a86952285a45a0a5d2562ac116d0d462a860b7c9b65dbacd86dd4d2138a33730e3ad770e5e38c385dcf40246b10e3845af9ce16738defa0fd07aceeea6c9a4a133bb39e739e73d9fcfbb7df1dfd34d00277043404c862004a23794356550574a4b8397166ea8053b049f40fb986694c62b8b8baa798c1dc85da727eba3d35b24acb2209350622149822120cd8d9350a6e7263d263d570582235a49b3470502a9b9f1fe59015faa7f36824e7485e1c7be0876634f1b247447d08e104b3d11445da997425428442adb3f35392c70b85e6b56d7d525451f33972aab6ac93e77bba0966daa39c4c5747ad66441292b05cdae9e4e0a1987058ed40368a53563451dccd9a6565a9a304a05c59e540ab66156433822b07f5559515df88a662f9364d94ac9b604be4b4ded8832addacb46f1bc522aeaaa7564ca30562ae5e1063737cbf0ab0ece54cb6a0bf7b9a9e6dd0cf7ef0c31a1e87a4eb3d5e108dec09b61bc8e547b10fd025d3cb61d41d92b13a3a50db0c74ebbbb9c63611cc2204b7bc3b488b768116b02215ea1b38983f573172bba7ed9d04ab66a366ce1dd30b16c9f40d2712c69c6a0cba64b6baab9a81bb71a5c4f86718a5d1b9898ab5ab6ba1a02656a534c53a9168c7255209dda39916c0b28cb4d9cc1681823789f4abfc51cf22a17b72398c03877f5016955365d605300ee1fd3fb0ea339f780a410fbfda97e26b7a42c3804ce129d0bbaaa984c6bd72da8deac28bad534d4dadae822c8cb8a453c2aaa24da863b6d81bda9163b1218aadfbfe45c8dc5c9334931902c1b96c68373355d5bd56c1693d7645c21dea6b2d957ad3d1f8300573252d0bd4bd95e779cd015cb0ae19ac0bea26a69a65a1cb32cd5e454395bb12b96d3f9d508bec07c18d7a144d081185fd382c0eeda059955f40a75e8771bdd4d270b2bd34a794659d0490fe78c8a5950273556a2e386615bb6a994dd5b6011e524fa260021f420486f0195340932e96d0d7a98f45d0d7a0431fe8c38327d449cdf28ff46bbb946d2f710425ca6138ba4711e897ebbd21bd89fce3cc181b4ef09e2e9cce613241e3b2c68c341f479ee43f091041cecf13f40c7331ccaf73cc5516003c79f3f8394eff94ddec0db8fc8436089dee4dd49e210def102e411007f27cf66fec4ae673891dfc07bcfd33f23414fbc78a637b3f980f153849f7e9ef1117a802d99cd5f71369d77a479aeaf9e220429f212fb89aa18c3b89726e6b5154cc73770ee91d387c464f7ece7a8082ee368436a7f43e65adef88c93d4df943308e1a59cc4875ec8bf08e58dbdd80e399a88ca2d62f6454f7eb917e7bffee6db991ae49fef8b1e6f060304a69ac12081c96630446077332813186d06a37c5ede8152801ac4ed46e556fd7673bf17f091d7ef75ea964738d930c240ab1146e53b1dd8faaab1dbb807e49d365f35db294c7bb93a201cda053884bb4d818bb8d4c21c5ff7cc97f1b167eea5d5b339ea529b6330bbd3b5409f20e779dea38e78874a3af32302fef5cc1f90ee23e05bcffc0e69da3938c0af1f18e364a4c56b4a829484a3f84679208907e8e12389a101e7ed98eefac4c3adbffdebdbedc62087e497e8641a138f85e80e906566bbb7618fa987fc231cd34f53fe1e5dd1bef832c7ee1d9a1fb92b6d3ddcfa275e0f2943fa176d427491fe2966bd407dded5ed74dae0e239de53cc01ee20647c8e2ecf39e5b8d26463d22f58b88f90ff21fcbe9f507cbc9d2540059fa5ff035876ee56009af7714993c36bcefbb3ff01504b0708aa958dda0205000065090000504b01021400140008080800d4ad282b2bc2bf352a000000280000001400040000000000000000000000000000004d4554412d494e462f4d414e49464553542e4d46feca0000504b01021400140008080800d4ad282bd51c32f69104000023080000070000000000000000000000000070000000412e636c617373504b01021400140008080800d4ad282bc7617e49330300002c060000070000000000000000000000000036050000422e636c617373504b01021400140008080800d4ad282bda5ceda9e10000003301000007000000000000000000000000009e080000432e636c617373504b01021400140008080800d4ad282baadb7187be0200007e0400000700000000000000000000000000b4090000442e636c617373504b01021400140008080800d4ad282b53839b74af000000d80000000700000000000000000000000000a70c0000452e636c617373504b01021400140008080800d4ad282baa958dda020500006509000007000000000000000000000000008b0d0000462e636c617373504b0506000000000700070084010000c2120000000000000042220000000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000000000000");
    }

    @Callable
    public static Address getStakerRegistry() {
        requireNoValue();
        return stakerRegistry;
    }

    /**
     * Registers a pool in the registry.
     *
     * @param signingAddress
     * @param commissionRate the pool commission rate
     * @return the pool coinbase address
     */
    @Callable
    public static void registerPool(Address signingAddress, int commissionRate, byte[] metaDataUrl, byte[] metaDataContentHash) {
        // sanity check
        require(commissionRate >= 0 && commissionRate <= 100);
        requireNoValue();
        // TODO: sanity checks on metaDataUrl and metaDataContentHash

        Address caller = Blockchain.getCaller();

        // make sure no one has registered as a staker using this identity
        require(!isStakerRegistered(caller));

        // make sure no one has registered as a pool using this identity
        require(!pools.containsKey(caller));

        Address poolRegistry =  Blockchain.getAddress();

        // step 1: deploy a coinbase contract
        System.arraycopy(poolRegistry.toByteArray(), 0, poolCoinbaseContract, poolCoinbaseContract.length - Address.LENGTH, Address.LENGTH);
        Result result = Blockchain.create(BigInteger.ZERO, poolCoinbaseContract, Blockchain.getRemainingEnergy());
        require(result.isSuccess());
        Address coinbaseAddress = new Address(result.getReturnData());

        // step 2: deploy a custodian contract
        System.arraycopy(poolRegistry.toByteArray(), 0, poolCustodianContract, poolCustodianContract.length - Address.LENGTH * 2 - 1, Address.LENGTH);
        System.arraycopy(stakerRegistry.toByteArray(), 0, poolCustodianContract, poolCustodianContract.length - Address.LENGTH, Address.LENGTH);
        result = Blockchain.create(BigInteger.ZERO, poolCustodianContract, Blockchain.getRemainingEnergy());
        require(result.isSuccess());
        Address custodianAddress = new Address(result.getReturnData());

        // step 3: create a staker in the staker registry
        /*
        registerStaker(Address identityAddress, Address managementAddress,
                                      Address signingAddress, Address coinbaseAddress, Address selfBondAddress)
         */
        byte[] registerStakerCall = new ABIStreamingEncoder()
                .encodeOneString("registerStaker")
                .encodeOneAddress(caller)
                .encodeOneAddress(poolRegistry)
                .encodeOneAddress(signingAddress)
                .encodeOneAddress(coinbaseAddress)
                .encodeOneAddress(custodianAddress)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, registerStakerCall, Blockchain.getRemainingEnergy());

        // step 4: add the pool registry as a listener in the staker registry
        byte[] addListenerCall = new ABIStreamingEncoder()
                .encodeOneString("addListener")
                .encodeOneAddress(caller)
                .encodeOneAddress(poolRegistry)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, addListenerCall, Blockchain.getRemainingEnergy());

        // step 5: update pool state
        PoolState ps = new PoolState(caller, coinbaseAddress, custodianAddress, commissionRate, metaDataUrl, metaDataContentHash);
        pools.put(caller, ps);
    }

    /**
     * Updates the signing address of a staker. Owner only.
     *
     * @param staker the staker address
     * @param newAddress the new signing address
     */
    @Callable
    public static void setSigningAddress(Address staker, Address newAddress) {
        requireNonNull(newAddress);
        requireNoValue();
        require(Blockchain.getCaller().equals(staker));
        requirePool(staker);

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("setSigningAddress")
                .encodeOneAddress(staker)
                .encodeOneAddress(newAddress)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
    }

    /**
     * Delegates stake to a pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static void delegate(Address pool) {
        Address caller = Blockchain.getCaller();
        BigInteger value = Blockchain.getValue();
        requirePool(pool);
        requirePositive(value);

        detectBlockRewards(pool);

        // transfers the value to the custodian contract if it's from the pool owner.
        // The reason for this is to make the stake (value) in case the pool misbehaves.
        PoolState ps = pools.get(pool);
        if (caller.equals(pool)) {
            secureCall(ps.custodianAddress, value, new byte[0], Blockchain.getRemainingEnergy());
        }

        delegate(caller, pool, Blockchain.getValue(), true);
    }

    private static void delegate(Address delegator, Address pool, BigInteger value, boolean doVote) {
        PoolState ps = pools.get(pool);

        if (doVote) {
            if (delegator.equals(pool)) {
                byte[] data = new ABIStreamingEncoder()
                        .encodeOneString("vote")
                        .encodeOneAddress(pool)
                        .encodeOneLong(value.longValue())
                        .toBytes();
                secureCall(ps.custodianAddress, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
            } else {
                byte[] data = new ABIStreamingEncoder()
                        .encodeOneString("vote")
                        .encodeOneAddress(pool)
                        .toBytes();
                secureCall(stakerRegistry, value, data, Blockchain.getRemainingEnergy());
            }
        }

        BigInteger previousStake = getOrDefault(ps.delegators, delegator, BigInteger.ZERO);
        ps.delegators.put(delegator, previousStake.add(value));

        // update rewards state machine
        ps.rewards.onVote(delegator, Blockchain.getBlockNumber(), value.longValue());

        // possible pool state change
        if (delegator.equals(ps.stakerAddress)) {
            checkPoolState(ps.stakerAddress);
        }
    }

    /**
     * Revokes stake to a pool.
     *
     * @param pool   the pool address
     * @param amount the amount of stake to undelegate
     */
    @Callable
    public static long undelegate(Address pool, long amount) {
        requirePool(pool);
        requirePositive(amount);
        requireNoValue();

        detectBlockRewards(pool);

        return undelegate(Blockchain.getCaller(), pool, amount, true);
    }

    private static long undelegate(Address delegator, Address pool, long amount, boolean doUnvote) {
        PoolState ps = pools.get(pool);

        BigInteger previousStake = getOrDefault(ps.delegators, delegator, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);
        require(previousStake.compareTo(amountBI) >= 0);
        ps.delegators.put(delegator, previousStake.subtract(amountBI));

        long id = -1;
        if (doUnvote) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("unvoteTo")
                    .encodeOneAddress(pool)
                    .encodeOneLong(amount)
                    .encodeOneAddress(delegator)
                    .toBytes();
            Result result = secureCall(
                    delegator.equals(pool) ? ps.custodianAddress : stakerRegistry,
                    BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
            id = new ABIDecoder(result.getReturnData()).decodeOneLong();
        }

        // update rewards state machine
        ps.rewards.onUnvote(delegator, Blockchain.getBlockNumber(), amount);

        // possible pool state change
        if (delegator.equals(ps.stakerAddress)) {
            checkPoolState(ps.stakerAddress);
        }

        return id;
    }

    /**
     * Delegates block rewards to a pool
     *
     * @param pool the pool address
     */
    @Callable
    public static void redelegate(Address pool) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requireNoValue();

        detectBlockRewards(pool);

        PoolState ps = pools.get(pool);

        // do a withdraw
        long amount = ps.rewards.onWithdraw(caller, Blockchain.getBlockNumber());
        if (caller.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        if (amount > 0) {
            // transfer the rewards to the custodian contract
            if (caller.equals(pool)) {
                secureCall(ps.custodianAddress, BigInteger.valueOf(amount), new byte[0], Blockchain.getRemainingEnergy());
            }

            delegate(caller, pool, BigInteger.valueOf(amount), true);
        }
    }

    private static class StakeTransfer {
        Address initiator;
        Address fromPool;
        Address toPool;
        Address recipient;
        long amount;

        public StakeTransfer(Address initiator, Address fromPool, Address toPool, Address recipient, long amount) {
            this.initiator = initiator;
            this.fromPool = fromPool;
            this.toPool = toPool;
            this.recipient = recipient;
            this.amount = amount;
        }
    }

    private static Map<Long, StakeTransfer> transfers = new AionMap<>();

    /**
     * Transfers stake from one pool to another pool.
     *
     * @param fromPool the from pool address
     * @param toPool   the to pool address
     * @param amount   the amount of stake to transfer
     * @return the pending transfer id
     */
    @Callable
    public static long transferStake(Address fromPool, Address toPool, long amount) {
        Address caller = Blockchain.getCaller();
        requirePool(fromPool);
        requirePool(toPool);
        requirePositive(amount);
        requireNoValue();
        require(!fromPool.equals(toPool));

        detectBlockRewards(fromPool);
        detectBlockRewards(toPool);

        PoolState ps = pools.get(fromPool);
        BigInteger previousStake1 = getOrDefault(ps.delegators, caller, BigInteger.ZERO);

        BigInteger amountBI = BigInteger.valueOf(amount);
        require(previousStake1.compareTo(amountBI) >= 0);
        ps.delegators.put(caller, previousStake1.subtract(amountBI));

        // update rewards state machine
        ps.rewards.onUnvote(caller, Blockchain.getBlockNumber(), amount);

        // if the stake is from the pool owner, transfer stake ownership back to the
        // pool registry, otherwise keep the stake in the custodian contract.
        Address recipient = caller.equals(fromPool) ? Blockchain.getAddress() : ps.custodianAddress;
        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("transferStakeTo")
                .encodeOneAddress(fromPool)
                .encodeOneAddress(toPool)
                .encodeOneLong(amount)
                .encodeOneAddress(recipient)
                .toBytes();
        Result result = secureCall(
                caller.equals(fromPool) ? ps.custodianAddress : stakerRegistry,
                BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

        long id = new ABIDecoder(result.getReturnData()).decodeOneLong();
        transfers.put(id, new StakeTransfer(caller, fromPool, toPool, recipient, amount));

        // possible pool state change
        if (caller.equals(ps.stakerAddress)) {
            checkPoolState(ps.stakerAddress);
        }

        return id;
    }

    /**
     * Returns the stake of a delegator to a pool.
     *
     * @param pool      the pool address
     * @param delegator the delegator address
     * @return the amount of stake
     */
    @Callable
    public static long getStake(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        return getOrDefault(pools.get(pool).delegators, delegator, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the self-bond stake to a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static long getSelfStake(Address pool) {
        requirePool(pool);
        requireNoValue();

        PoolState ps = pools.get(pool);
        return getOrDefault(ps.delegators, ps.stakerAddress, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the total stake of a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static long getTotalStake(Address pool) {
        requirePool(pool);
        requireNoValue();

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("getTotalStake")
                .encodeOneAddress(pool)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneLong();
    }

    /**
     * Finalizes an un-vote operation.
     *
     * @param id pending unvote id
     */
    @Callable
    public static void finalizeUnvote(long id) {
        requireNoValue();

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("finalizeUnvote")
                .encodeOneLong(id)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
    }

    /**
     * Finalizes a transfer operation.
     *
     * @param id pending transfer id
     */
    @Callable
    public static void finalizeTransfer(long id) {
        requireNoValue();

        require(transfers.containsKey(id));

        StakeTransfer transfer = transfers.remove(id);

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("finalizeTransfer")
                .encodeOneLong(id)
                .toBytes();
        secureCall(
                transfer.initiator.equals(transfer.fromPool) ? pools.get(transfer.fromPool).custodianAddress : stakerRegistry,
                BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

        delegate(transfer.initiator, transfer.toPool, BigInteger.valueOf(transfer.amount), false);
    }

    /**
     * Returns the auto-redelegation fee set by a delegator, or -1 if not set.
     *
     * @param pool      the pool's address
     * @param delegator the delegator's address
     * @return the fee in percentage, or -1
     */
    @Callable
    public static int getAutoRewardsDelegationFee(Address pool, Address delegator) {
        requirePool(pool);
        requireNoValue();

        return getOrDefault(pools.get(pool).autoRewardsDelegationDelegators, delegator, -1);
    }

    /**
     * Enables auto-redelegation on a pool.
     *
     * @param pool the pool address
     * @param feePercentage the auto-redelegation fee
     */
    @Callable
    public static void enableAutoRewardsDelegation(Address pool, int feePercentage) {
        requirePool(pool);
        require(feePercentage >= 0 && feePercentage <= 100);
        requireNoValue();

        pools.get(pool).autoRewardsDelegationDelegators.put(Blockchain.getCaller(), feePercentage);
    }

    /**
     * Disables auto-redelegation on a pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static void disableAutoRewardsDedelegation(Address pool) {
        requirePool(pool);
        requireNoValue();

        pools.get(pool).autoRewardsDelegationDelegators.remove(Blockchain.getCaller());
    }

    /**
     * Delegates one delegator's block rewards to the pool. The caller
     * gets the auto-redelegation fee.
     *
     * @param pool the pool address
     * @param delegator the delegator address
     */
    @Callable
    public static void autoDelegateRewards(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        detectBlockRewards(pool);

        // check auto-redelegation authorization
        PoolState ps = pools.get(pool);
        require(ps.autoRewardsDelegationDelegators.containsKey(delegator));

        // do a withdraw
        long amount = ps.rewards.onWithdraw(delegator, Blockchain.getBlockNumber());
        if (delegator.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        Blockchain.println("Auto delegation: rewards = " + amount);

        if (amount > 0) {
            long fee = amount * ps.autoRewardsDelegationDelegators.get(delegator) / 100;
            long remaining = amount - fee;

            Blockchain.println("Auto delegation: fee = " + fee + ", remaining = " + remaining);

            // transfer fee to the caller
            secureCall(Blockchain.getCaller(), BigInteger.valueOf(fee), new byte[0], Blockchain.getRemainingEnergy());

            // use the remaining rewards to delegate
            if (delegator.equals(pool)) {
                secureCall(ps.custodianAddress, BigInteger.valueOf(remaining), new byte[0], Blockchain.getRemainingEnergy());
            }
            delegate(delegator, pool, BigInteger.valueOf(remaining), true);
        }
    }

    /**
     * Delegates to a pool and enables auto-redelegation.
     *
     * @param pool the pool address
     * @param fee the auto-redelegation fee
     */
    @Callable
    public static void delegateAndEnableAutoRedelegation(Address pool, int fee) {
        requirePool(pool);
        require(fee >= 0 && fee <= 100);
        requirePositive(Blockchain.getValue());

        delegate(Blockchain.getCaller(), pool, Blockchain.getValue(), true);
        enableAutoRewardsDelegation(pool, fee);
    }

    /**
     * Returns the outstanding rewards of a delegator.
     *
     * @param pool      the pool address
     * @param delegator the delegator address
     * @return the amount of outstanding rewards
     */
    public static long getRewards(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        return pools.get(pool).rewards.getRewards(delegator, Blockchain.getBlockNumber());
    }

    /**
     * Withdraws block rewards from one pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static long withdraw(Address pool) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requireNoValue();

        detectBlockRewards(pool);

        // query withdraw amount from rewards state machine
        PoolState ps = pools.get(pool);
        long amount = ps.rewards.onWithdraw(caller, Blockchain.getBlockNumber());
        if (caller.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        // do a transfer
        if (amount > 0) {
            secureCall(caller, BigInteger.valueOf(amount), new byte[0], Blockchain.getRemainingEnergy());
        }
        return amount;
    }

    /**
     * Returns the status of a pool.
     *
     * @param pool the pool address.
     * @return
     */
    @Callable
    public static String getPoolStatus(Address pool) {
        requirePool(pool);
        requireNoValue();
        return pools.get(pool).isActive ? "ACTIVE" : "BROKEN";
    }

    @Callable
    public static void onSigningAddressChange(Address staker, Address newSigningAddress) {

        // do nothing
    }

    @Callable
    public static void onCoinbaseAddressChange(Address staker, Address newCoinbaseAddress) {
        onlyStakerRegistry();
        requireNonNull(newCoinbaseAddress);
        requireNoValue();

        checkPoolState(staker);
    }

    @Callable
    public static void onListenerAdded(Address staker) {
        onlyStakerRegistry();
        requireNoValue();

        checkPoolState(staker);
    }

    @Callable
    public static void onListenerRemoved(Address staker) {
        onlyStakerRegistry();
        requireNoValue();

        checkPoolState(staker);
    }

    @Callable
    public static void onSlashing(Address staker, long amount) {
        PoolState ps = pools.get(staker);
        if (ps != null) {
            // the slashing amount should be greater than the stake
            require(getStake(staker, staker) >= amount);

            // do a un-delegate
            undelegate(staker, staker, amount, false);

            // check pool state
            checkPoolState(staker);
        }
    }

    private static void checkPoolState(Address staker) {
        PoolState ps = pools.get(staker);
        if (ps != null) {
            boolean active = isActive(staker);
            if (ps.isActive && !active) {
                switchToBroken(ps);
            }
            if (!ps.isActive && active) {
                switchToActive(ps);
            }
        }
    }

    private static boolean isActive(Address pool) {
        // TODO: optimize - checking all three condition each time costs too much energy
        return isCoinbaseSetup(pool) && isListenerSetup(pool) && isSelfStakeSatisfied(pool) && isStakerActive(pool);
    }

    private static boolean isStakerRegistered(Address staker) {
        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("isStaker")
                .encodeOneAddress(staker)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        boolean isStaker = new ABIDecoder(result.getReturnData()).decodeOneBoolean();

        return isStaker;
    }

    private static boolean isStakerActive(Address pool) {
        requirePool(pool);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("isActive")
                .encodeOneAddress(pool)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneBoolean();
    }

    private static boolean isCoinbaseSetup(Address pool) {
        requirePool(pool);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("getCoinbaseAddressForIdentityAddress")
                .encodeOneAddress(pool)
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        Address coinbaseAddress = new ABIDecoder(result.getReturnData()).decodeOneAddress();

        PoolState ps = pools.get(pool);
        return ps.coinbaseAddress.equals(coinbaseAddress);
    }

    private static boolean isListenerSetup(Address pool) {
        requirePool(pool);

        byte[] txData = new ABIStreamingEncoder()
                .encodeOneString("isListener")
                .encodeOneAddress(pool)
                .encodeOneAddress(Blockchain.getAddress())
                .toBytes();
        Result result = secureCall(stakerRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
        return new ABIDecoder(result.getReturnData()).decodeOneBoolean();
    }

    private static boolean isSelfStakeSatisfied(Address pool) {
        requirePool(pool);

        PoolState ps = pools.get(pool);
        BigInteger stake = getOrDefault(ps.delegators, ps.stakerAddress, BigInteger.ZERO);

        // can implement a self-bond percentage very easily here
        return stake.compareTo(MIN_SELF_STAKE) >= 0;
    }


    private static void switchToActive(PoolState ps) {
        ps.isActive = true;
        ps.rewards.setCommissionRate(ps.commissionRate);
    }

    private static void switchToBroken(PoolState ps) {
        ps.isActive = false;
        ps.rewards.setCommissionRate(0);

        // alternatively, punishment could be making the staker inactive
    }

    private static void require(boolean condition) {
        Blockchain.require(condition);
    }

    private static void requireNonNull(Object obj) {
        require(obj != null);
    }

    private static void requireNoValue() {
        require(Blockchain.getValue().equals(BigInteger.ZERO));
    }

    private static void requirePool(Address pool) {
        require(pool != null && pools.containsKey(pool));
    }

    private static void requirePositive(BigInteger num) {
        require(num != null && num.compareTo(BigInteger.ZERO) > 0);
    }

    private static void requirePositive(long num) {
        require(num > 0);
    }

    private static void onlyStakerRegistry() {
        Address caller = Blockchain.getCaller();
        require(caller.equals(stakerRegistry));
    }

    private static byte[] hexStringToByteArray(String s) {
        // TODO: use static variable
        int[] map = new int[256];
        int value = 0;
        for (char c : "0123456789abcdef".toCharArray()) {
            map[c] = value++;
        }

        char[] chars = s.toCharArray();
        int length = chars.length;
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) ((map[chars[i]] << 4) + map[chars[i + 1]]);
        }
        return result;
    }

    private static Result secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
        return result;
    }


    private static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            return defaultValue;
        }
    }

    private static void detectBlockRewards(Address pool) {
        PoolState ps = pools.get(pool);

        BigInteger balance = Blockchain.getBalance(ps.coinbaseAddress);
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("transfer")
                    .encodeOneAddress(Blockchain.getAddress())
                    .encodeOneLong(balance.longValue())
                    .toBytes();
            secureCall(ps.coinbaseAddress, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

            ps.rewards.onBlock(Blockchain.getBlockNumber(), balance.longValue());

            Blockchain.println("New block rewards: " + balance);
        }
    }
}
