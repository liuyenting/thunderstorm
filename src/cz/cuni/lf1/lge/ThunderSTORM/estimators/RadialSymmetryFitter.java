package cz.cuni.lf1.lge.ThunderSTORM.estimators;

import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSFInstance;
import ij.plugin.filter.Convolver;
import ij.process.FloatProcessor;

/**
 * Rewritten to java from the matlab implementation in supplement of the article
 * "Rapid, accurate particle tracking by calculation of radial symmetry centers"
 * by Raghuveer Parthasarathy
 *
 */
public class RadialSymmetryFitter implements OneLocationFitter {

  public static final String[] paramNames = {PSFInstance.X, PSFInstance.Y};

  @Override
  public PSFInstance fit(SubImage img) {
    float[] dIdu = computeGradientImage(img, false);
    float[] dIdv = computeGradientImage(img, true);

    smooth(dIdu, img.size);
    smooth(dIdv, img.size);

    float[] m = calculateSlope(dIdu, dIdv);
    float[] xMesh = createMesh(img.size, true);
    float[] yMesh = createMesh(img.size, false);

    float[] yInterceptB = calculateYIntercept(xMesh, yMesh, m);

    float[] weights = calculateWeights(dIdu, dIdv, xMesh, yMesh);

    double[] coordinates = lsRadialCenterFit(m, yInterceptB, weights);

    return new PSFInstance(paramNames, coordinates);
  }

  /**
   * Computes gradient image along 45-degree shifted coordinates.
   *
   * @param img
   * @param mainDiagonalDirection specifies the direction in which the gradient
   * will be computed, true for main diagonal and false for antidiagonal
   * @return
   */
  private float[] computeGradientImage(SubImage img, boolean mainDiagonalDirection) {
    float[] dI = new float[(img.size - 1) * (img.size - 1)];

    int idx1 = mainDiagonalDirection ? 0 : 1;
    int idx2 = mainDiagonalDirection ? img.size + 1 : img.size;
    int resIdx = 0;

    for (int i = 0; i < img.size - 1; i++) {
      for (int j = 0; j < img.size - 1; j++) {
        dI[resIdx++] = (float) (img.values[idx1++] - img.values[idx2++]);
      }
      idx1++;
      idx2++;
    }

    return dI;
  }

  private void smooth(float[] dIdu, int size) {
    float[] kernel = {1f / 3f, 1f / 3f, 1f / 3f};

    Convolver convolver = new Convolver();
    FloatProcessor imp = new FloatProcessor(size - 1, size - 1, dIdu);
    convolver.convolve(imp, kernel, kernel.length, 1);
    convolver.convolve(imp, kernel, 1, kernel.length);
  }

  private float[] calculateSlope(float[] dIdu, float[] dIdv) {
    float[] m = new float[dIdu.length];

    for (int i = 0; i < m.length; i++) {
      float val = -(dIdu[i] + dIdv[i]) / (dIdu[i] - dIdv[i]);
      val = Float.isNaN(val) ? 0 : val;
      val = Float.isInfinite(val) ? Float.MAX_VALUE / 1e5f : val; //replace inf by some big value - Not max_value because it could overflow to infinity in next step
      m[i] = val;
    }
    return m;
  }

  private float[] createMesh(int size, boolean xMesh) {
    float[] mesh = new float[(size - 1) * (size - 1)];
    int smallSize = (size - 1) / 2;

    int idx = 0;
    for (int i = 0; i < size - 1; i++) {
      float iVal = -smallSize + 0.5f + i;
      for (int j = 0; j < size - 1; j++) {
        float jVal = -smallSize + 0.5f + j;
        mesh[idx++] = xMesh ? jVal : iVal;
      }
    }
    return mesh;
  }

  private float[] calculateYIntercept(float[] xMesh, float[] yMesh, float[] m) {
    float[] intercept = new float[m.length];
    for (int i = 0; i < intercept.length; i++) {
      intercept[i] = yMesh[i] - m[i] * xMesh[i];
    }
    return intercept;
  }

  private float[] calculateWeights(float[] dIdu, float[] dIdv, float[] xMesh, float[] yMesh) {
    float gradientMagnitudeSum = 0;
    float xCentroid = 0;
    float yCentroid = 0;
    for (int i = 0; i < dIdu.length; i++) {
      float gradientMagnitude = dIdu[i] * dIdu[i] + dIdv[i] * dIdv[i];
      gradientMagnitudeSum += gradientMagnitude;

      xCentroid += xMesh[i] * gradientMagnitude;
      yCentroid += yMesh[i] * gradientMagnitude;
    }
    xCentroid /= gradientMagnitudeSum;
    yCentroid /= gradientMagnitudeSum;

    float[] weights = new float[dIdu.length];

    for (int i = 0; i < weights.length; i++) {
      float gradientMagnitude = dIdu[i] * dIdu[i] + dIdv[i] * dIdv[i];
      double distanceToCentroid = Math.sqrt((xMesh[i] - xCentroid) * (xMesh[i] - xCentroid) + (yMesh[i] - yCentroid) * (yMesh[i] - yCentroid));
      weights[i] = (float) (gradientMagnitude / distanceToCentroid);
    }
    return weights;
  }

  private double[] lsRadialCenterFit(float[] m, float[] b, float[] weights) {
    float sw = 0;
    float smmw = 0;
    float smw = 0;
    float smbw = 0;
    float sbw = 0;

    for (int i = 0; i < m.length; i++) {
      float weighted = weights[i] / (m[i] * m[i] + 1); //wm2p1
      sw += weighted;
      float mw = weighted * m[i];
      smw += mw;
      smmw += mw * m[i];
      smbw += mw * b[i];
      sbw += weighted * b[i];
    }
    float det = smw * smw - smmw * sw;
    float xc = (smbw * sw - smw * sbw) / det;    // relative to image center
    float yc = (smbw * smw - smmw * sbw) / det; // relative to image center

    return new double[]{xc, yc};
  }
}