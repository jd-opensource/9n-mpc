Generate Mnist Data for FL
---------------
```python
# First time use. Download mnist data. 
python make_data.py --download=1 --output_dir='.' --partition_num=10 --drop_rate=-1
```

```python
# Mnist data already exists. Rerun and Randomly drop data using drop_rate.
python make_data.py -d=0 -o='.' -p 10 -r=0.1
```
