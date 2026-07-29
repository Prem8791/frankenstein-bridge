package android.app.prodx;

import android.app.prodx.ProdXGrant;

interface IProdXGrantAdmin {
    List<ProdXGrant> getGrants(int userId);
    ProdXGrant getGrant(String grantId);
    boolean revokeGrant(in String grantId);
    boolean suspendGrant(in String grantId);
    List<ProdXGrant> getGrantsByPackage(String packageName, int userId);
}
